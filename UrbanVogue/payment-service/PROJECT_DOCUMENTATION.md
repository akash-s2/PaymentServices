# UrbanVogue Payment Microservice — Project Documentation

A complete file-by-file explanation of every file in the payment microservice: **why it exists**, **what it does**, and **how the code works**.

---

## Table of Contents

1. [Project Configuration](#1-project-configuration)
   - [pom.xml](#11-pomxml)
   - [application.properties](#12-applicationproperties)
   - [PaymentServiceApplication.java](#13-paymentserviceapplicationjava)
   - [AppConfig.java](#14-appconfigjava)
2. [Entity Layer](#2-entity-layer)
   - [PaymentStatus.java](#21-paymentstatusjava)
   - [PaymentMethod.java](#22-paymentmethodjava)
   - [Payment.java](#23-paymentjava)
3. [Repository Layer](#3-repository-layer)
   - [PaymentRepository.java](#31-paymentrepositoryjava)
4. [DTO Layer](#4-dto-layer)
   - [PaymentRequest.java](#41-paymentrequestjava)
   - [PaymentResponse.java](#42-paymentresponsejava)
   - [OrderResponse.java](#43-orderresponsejava)
   - [OrderStatusUpdateRequest.java](#44-orderstatusupdaterequestjava)
5. [Mapper Layer](#5-mapper-layer)
   - [PaymentMapper.java](#51-paymentmapperjava)
6. [Simulator Layer](#6-simulator-layer)
   - [FailureReason.java](#61-failurereasonjava)
   - [PaymentSimulationResult.java](#62-paymentsimulationresultjava)
   - [PaymentSimulator.java](#63-paymentsimulatorjava)
   - [PaymentSimulatorProperties.java](#64-paymentsimulatorpropertiesjava)
7. [Client Layer](#7-client-layer)
   - [OrderClient.java](#71-orderclientjava)
8. [Service Layer](#8-service-layer)
   - [PaymentService.java](#81-paymentservicejava)
9. [Exception Handling](#9-exception-handling)
   - [PaymentNotFoundException.java](#91-paymentnotfoundexceptionjava)
   - [InvalidPaymentException.java](#92-invalidpaymentexceptionjava)
   - [GlobalExceptionHandler.java](#93-globalexceptionhandlerjava)
10. [Controller Layer](#10-controller-layer)
    - [PaymentController.java](#101-paymentcontrollerjava)

---

## 1. Project Configuration

### 1.1 pom.xml

**📁 Path:** `payment-service/pom.xml`

**Why it exists:**
Every Maven-based Java project needs a `pom.xml`. It is the Project Object Model — it tells Maven what your project is, which version of Java to compile with, and which libraries (dependencies) to download.

**What it does:**
- Declares the project as a **Spring Boot 3.2.5** application using `spring-boot-starter-parent` as the parent POM (which pre-configures sensible defaults for Spring Boot).
- Sets the group ID to `com.urbanvogue` and artifact to `payment-service`.
- Targets **Java 17**.
- Pulls in exactly 5 dependencies:

| Dependency | Purpose |
|---|---|
| `spring-boot-starter-web` | Embedded Tomcat + Spring MVC for building REST APIs |
| `spring-boot-starter-data-jpa` | Spring Data JPA + Hibernate for database access |
| `spring-boot-starter-validation` | Jakarta Bean Validation (`@NotNull`, `@Valid`, etc.) |
| `h2` (runtime) | Lightweight embedded database for development |
| `spring-boot-starter-test` (test) | JUnit 5 + Mockito + Spring Test utilities |

- Configures the `spring-boot-maven-plugin` for building executable JARs.

**Key design decision:** No Lombok dependency — all boilerplate is written explicitly, making the code transparent and debug-friendly.

---

### 1.2 application.properties

**📁 Path:** `payment-service/src/main/resources/application.properties`

**Why it exists:**
Spring Boot reads this file at startup to configure the application. It externalizes all tunable parameters so you can change behavior without modifying code.

**What it does:**

```properties
# The payment service runs on port 8082 (separate from order service on 8081)
server.port=8082

# H2 file-based database — persists data to ./data/payment on disk
spring.datasource.url=jdbc:h2:file:./data/payment;DB_CLOSE_DELAY=-1;AUTO_SERVER=TRUE

# JPA auto-creates/updates tables from @Entity classes
spring.jpa.hibernate.ddl-auto=update

# H2 web console at http://localhost:8082/h2-console for debugging
spring.h2.console.enabled=true

# The URL where the Order Service is running
order.service.base-url=http://localhost:8081

# 80% chance a simulated payment succeeds
payment.simulator.success-rate=0.8

# When a payment fails, these weights determine how likely each reason is:
# INSUFFICIENT_FUNDS=30, CARD_EXPIRED=20, BANK_DECLINED=25,
# FRAUD_SUSPECTED=10, NETWORK_ERROR=15
# Weights are relative — they do NOT need to sum to 100.
```

**Key design decision:** Failure reasons use **relative weights** (not percentages), so you can add/remove/adjust reasons without worrying about totals summing to 100.

---

### 1.3 PaymentServiceApplication.java

**📁 Path:** `com.urbanvogue.payment.PaymentServiceApplication`

**Why it exists:**
Every Spring Boot application needs exactly one class annotated with `@SpringBootApplication`. This is the entry point that bootstraps the entire application.

**What it does:**

```java
@SpringBootApplication
@EnableConfigurationProperties(PaymentSimulatorProperties.class)
public class PaymentServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}
```

- `@SpringBootApplication` — combines three annotations: `@Configuration`, `@EnableAutoConfiguration`, and `@ComponentScan`. It tells Spring to auto-configure everything and scan for beans in this package and below.
- `@EnableConfigurationProperties(PaymentSimulatorProperties.class)` — activates the binding of `payment.simulator.failure-reasons.*` properties into the `PaymentSimulatorProperties` class. Without this, Spring wouldn't know to bind those map-style properties.
- `main()` — starts the embedded Tomcat server and initializes the Spring context.

---

### 1.4 AppConfig.java

**📁 Path:** `com.urbanvogue.payment.config.AppConfig`

**Why it exists:**
The `OrderClient` needs a `RestTemplate` to make HTTP calls to the Order Service. `RestTemplate` isn't auto-configured by Spring Boot, so we need to define it as a bean ourselves.

**What it does:**

```java
@Configuration
public class AppConfig {
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
```

- `@Configuration` — marks this class as a source of bean definitions.
- `@Bean` — tells Spring to manage the `RestTemplate` instance. Now any class (like `OrderClient`) can request it via constructor injection.

**Why not create RestTemplate directly in OrderClient?** Because making it a shared bean means:
1. It's reusable across multiple clients
2. You can easily customize it later (timeouts, interceptors, logging) in one place
3. It's mockable in tests

---

## 2. Entity Layer

### 2.1 PaymentStatus.java

**📁 Path:** `com.urbanvogue.payment.entity.PaymentStatus`

**Why it exists:**
A payment goes through a defined lifecycle: `INITIATED → SUCCESS` or `INITIATED → FAILED`. Using an enum enforces type safety — you can never set an invalid status like `"PENDNG"` (typo).

**What it does:**

```java
public enum PaymentStatus {
    INITIATED,  // Payment created, gateway not yet called
    SUCCESS,    // Payment gateway confirmed the charge
    FAILED      // Payment gateway declined
}
```

Stored in the DB as a string (via `@Enumerated(EnumType.STRING)` on the entity) for readability.

---

### 2.2 PaymentMethod.java

**📁 Path:** `com.urbanvogue.payment.entity.PaymentMethod`

**Why it exists:**
Constrains the set of valid payment methods. The API client must send one of these exact values — anything else gets rejected at the validation layer.

**What it does:**

```java
public enum PaymentMethod {
    CREDIT_CARD, DEBIT_CARD, UPI, NET_BANKING, WALLET
}
```

---

### 2.3 Payment.java

**📁 Path:** `com.urbanvogue.payment.entity.Payment`

**Why it exists:**
This is the **core domain entity** — the single most important class in the project. It represents a payment record in the database and enforces all business invariants.

**What it does:**

**Fields:**

| Field | Type | Purpose |
|---|---|---|
| `id` | `Long` | Auto-generated primary key |
| `orderId` | `Long` | Links to the order being paid (unique — one payment per order) |
| `amount` | `BigDecimal` | Payment amount (copied from order) |
| `status` | `PaymentStatus` | Current lifecycle state |
| `method` | `PaymentMethod` | How the customer is paying |
| `transactionId` | `String` | Gateway-issued transaction ID (set on SUCCESS) |
| `idempotencyKey` | `String` | Unique key from the client to prevent duplicate payments |
| `orderUpdated` | `boolean` | Whether the Order Service has been notified |
| `failureReason` | `String` | Why the payment failed (null on SUCCESS) |
| `createdAt` | `LocalDateTime` | Timestamp, auto-set via `@PrePersist` |

**Key design principles:**

1. **No public setters** — Fields can only change through controlled methods:
   - `markSuccess(transactionId)` — Sets status to SUCCESS and records the txn ID. Throws `IllegalStateException` if not in INITIATED state.
   - `markFailed(failureReason)` — Sets status to FAILED with a reason. Same guard.
   - `markOrderUpdated()` — Flags that the Order Service was notified.

2. **Protected no-arg constructor** — JPA (Hibernate) requires a no-arg constructor to instantiate entities from DB rows, but it's `protected` to prevent external misuse.

3. **Public constructor** — The only way to create a Payment: you must provide `orderId`, `amount`, `method`, and `idempotencyKey`. Status is automatically set to `INITIATED`.

4. **`@PrePersist`** — `createdAt` is automatically set when the entity is first saved. It's `updatable = false` so it can never be changed.

**Why this matters:** In a real payment system, letting anyone call `payment.setStatus("SUCCESS")` would be catastrophic. Controlled methods ensure that state transitions are always valid and always carry required data (e.g., you can't have SUCCESS without a transactionId).

---

## 3. Repository Layer

### 3.1 PaymentRepository.java

**📁 Path:** `com.urbanvogue.payment.repository.PaymentRepository`

**Why it exists:**
Spring Data JPA eliminates boilerplate database code. By extending `JpaRepository`, you get full CRUD operations (`save`, `findById`, `findAll`, `delete`, etc.) for free. This interface adds custom queries specific to the payment domain.

**What it does:**

```java
@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByIdempotencyKey(String idempotencyKey);
    Optional<Payment> findByOrderId(Long orderId);
    boolean existsByOrderIdAndStatus(Long orderId, PaymentStatus status);
}
```

| Method | Used For |
|---|---|
| `findByIdempotencyKey` | Idempotency check — has this request been processed before? |
| `findByOrderId` | GET endpoint — look up payment by order |
| `existsByOrderIdAndStatus` | Prevent duplicate success — is there already a SUCCESS payment for this order? |

Spring Data JPA generates the SQL automatically from the method names — no query writing needed.

---

## 4. DTO Layer

### 4.1 PaymentRequest.java

**📁 Path:** `com.urbanvogue.payment.dto.PaymentRequest`

**Why it exists:**
Never expose your entity directly to the API — DTOs (Data Transfer Objects) decouple the API contract from the database model. This record defines exactly what the client needs to send.

**What it does:**

```java
public record PaymentRequest(
    @NotNull(message = "Order ID is required") Long orderId,
    @NotNull(message = "Payment method is required") PaymentMethod paymentMethod
) {}
```

- **Java Record** — immutable by nature. No setters, no equals/hashCode boilerplate. Ideal for DTOs.
- `@NotNull` — Jakarta validation annotations. If a client sends `null` for either field, Spring returns a 400 error automatically (handled by `GlobalExceptionHandler`).
- Notice the client does **not** send the `amount` — it's fetched from the Order Service to prevent tampering.

---

### 4.2 PaymentResponse.java

**📁 Path:** `com.urbanvogue.payment.dto.PaymentResponse`

**Why it exists:**
The API response DTO. Carries the full payment state back to the client after processing.

**What it does:**

```java
public record PaymentResponse(
    Long id, Long orderId, BigDecimal amount,
    PaymentStatus status, PaymentMethod method,
    String transactionId, String idempotencyKey,
    boolean orderUpdated, String failureReason,
    LocalDateTime createdAt
) {}
```

Maps 1-to-1 with the Payment entity fields. Having a separate DTO means you can later change the entity without breaking the API (or vice versa).

---

### 4.3 OrderResponse.java

**📁 Path:** `com.urbanvogue.payment.dto.OrderResponse`

**Why it exists:**
When the payment service calls the Order Service's `GET /orders/{orderId}`, it needs a class to deserialize the JSON response into. This record represents what the Order Service returns.

**What it does:**

```java
public record OrderResponse(
    Long orderId, Long userId,
    BigDecimal totalAmount, String status
) {}
```

- `totalAmount` is used as the payment amount.
- `status` is checked to ensure it's `"CREATED"` before allowing payment.
- `userId` is available for future JWT-based ownership validation.

---

### 4.4 OrderStatusUpdateRequest.java

**📁 Path:** `com.urbanvogue.payment.dto.OrderStatusUpdateRequest`

**Why it exists:**
When calling `PUT /orders/{orderId}/status` on the Order Service, we need a request body. This simple record wraps the new status string.

**What it does:**

```java
public record OrderStatusUpdateRequest(String status) {}
```

Sent as JSON: `{"status": "PAID"}` or `{"status": "PAYMENT_FAILED"}`.

---

## 5. Mapper Layer

### 5.1 PaymentMapper.java

**📁 Path:** `com.urbanvogue.payment.mapper.PaymentMapper`

**Why it exists:**
Centralizes all entity ↔ DTO conversion logic. Without it, conversion code would be scattered across the service and controller, violating the Single Responsibility Principle.

**What it does:**

```java
@Component
public class PaymentMapper {

    // Entity → Response DTO
    public PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(
            payment.getId(), payment.getOrderId(), payment.getAmount(),
            payment.getStatus(), payment.getMethod(), payment.getTransactionId(),
            payment.getIdempotencyKey(), payment.isOrderUpdated(),
            payment.getFailureReason(), payment.getCreatedAt()
        );
    }

    // Request + Order data → Entity
    public Payment toEntity(PaymentRequest request,
                            OrderResponse order,
                            String idempotencyKey) {
        return new Payment(
            request.orderId(), order.totalAmount(),
            request.paymentMethod(), idempotencyKey
        );
    }
}
```

- `toResponse()` — Used every time we return a payment to the client (after processing or on GET).
- `toEntity()` — Creates a new Payment. Note it takes `order.totalAmount()` as the amount — the client never controls the amount.

---

## 6. Simulator Layer

### 6.1 FailureReason.java

**📁 Path:** `com.urbanvogue.payment.simulator.FailureReason`

**Why it exists:**
In a real system, payment gateways return specific decline codes. This enum models realistic failure reasons for our simulator.

**What it does:**

```java
public enum FailureReason {
    INSUFFICIENT_FUNDS,  // Customer doesn't have enough balance
    CARD_EXPIRED,        // Card's expiration date has passed
    BANK_DECLINED,       // Issuing bank rejected the transaction
    FRAUD_SUSPECTED,     // Anti-fraud system flagged the transaction
    NETWORK_ERROR        // Communication failure with the bank
}
```

Each reason has a configurable weight in `application.properties` that controls how often it's selected.

---

### 6.2 PaymentSimulationResult.java

**📁 Path:** `com.urbanvogue.payment.simulator.PaymentSimulationResult`

**Why it exists:**
The simulator needs to return two pieces of information: whether the payment succeeded, and if not, why. This record bundles both into a single immutable return value.

**What it does:**

```java
public record PaymentSimulationResult(
    boolean success,
    FailureReason failureReason  // null when success = true
) {
    public static PaymentSimulationResult ofSuccess() {
        return new PaymentSimulationResult(true, null);
    }
    public static PaymentSimulationResult ofFailure(FailureReason reason) {
        return new PaymentSimulationResult(false, reason);
    }
}
```

- Factory methods `ofSuccess()` and `ofFailure()` improve readability at call sites.
- Named `ofSuccess`/`ofFailure` (not `success`/`failure`) to avoid clashing with the auto-generated `success()` accessor that Java records create for the `boolean success` component.

---

### 6.3 PaymentSimulator.java

**📁 Path:** `com.urbanvogue.payment.simulator.PaymentSimulator`

**Why it exists:**
Since we don't have a real payment gateway (Stripe, Razorpay, etc.), this class **simulates** one with configurable behavior. You can tune the success rate and failure reason distribution without changing code.

**What it does:**

```java
@Component
public class PaymentSimulator {
    private final double successRate;              // e.g. 0.8 = 80%
    private final Map<String, Integer> failureReasonWeights;  // e.g. {"INSUFFICIENT_FUNDS": 30, ...}
    private final Random random;
```

**`simulate()` method:**
1. Generates a random number between 0.0 and 1.0.
2. If it's less than `successRate` (0.8), return `ofSuccess()`.
3. Otherwise, call `pickFailureReason()` to choose a failure reason.

**`pickFailureReason()` — weighted random selection:**
1. Sum all the weights (e.g. 30+20+25+10+15 = 100).
2. Generate a random number from 0 to total (e.g. 0–99).
3. Walk through the reasons, accumulating weights. When the cumulative sum exceeds the random number, that reason is selected.

**Example:** If the random roll is 42:
- `INSUFFICIENT_FUNDS` covers 0–29 → no
- `CARD_EXPIRED` covers 30–49 → **yes** (42 falls here)

This is a classic weighted random selection algorithm.

---

### 6.4 PaymentSimulatorProperties.java

**📁 Path:** `com.urbanvogue.payment.simulator.PaymentSimulatorProperties`

**Why it exists:**
Spring Boot's `@Value` annotation can inject simple values, but it **cannot** inject nested map-style properties like `payment.simulator.failure-reasons.INSUFFICIENT_FUNDS=30`. You need `@ConfigurationProperties` for that.

**What it does:**

```java
@Component
@ConfigurationProperties(prefix = "payment.simulator")
public class PaymentSimulatorProperties {
    private Map<String, Integer> failureReasons = new HashMap<>();
    // getter and setter (required by Spring's property binder)
}
```

- `@ConfigurationProperties(prefix = "payment.simulator")` — tells Spring to bind all properties starting with `payment.simulator.*` into this class.
- `failureReasons` maps to `payment.simulator.failure-reasons.*` — Spring converts the kebab-case property names to camelCase field names automatically.
- This is the **only class with setters** in the project. The setter is required by Spring's property binding mechanism — this is infrastructure code, not domain code, so the constraint is acceptable.

---

## 7. Client Layer

### 7.1 OrderClient.java

**📁 Path:** `com.urbanvogue.payment.client.OrderClient`

**Why it exists:**
The payment service needs to communicate with the Order Service to:
1. Fetch order details (amount, status) before processing payment
2. Update the order's status after payment (PAID or PAYMENT_FAILED)

This class encapsulates all HTTP communication with the Order Service.

**What it does:**

```java
@Component
public class OrderClient {
    private final RestTemplate restTemplate;
    private final String orderServiceBaseUrl;  // from application.properties

    // GET /orders/{orderId} — fetch order details
    public OrderResponse getOrder(Long orderId) {
        String url = orderServiceBaseUrl + "/orders/" + orderId;
        return restTemplate.getForObject(url, OrderResponse.class);
    }

    // PUT /orders/{orderId}/status — update order status
    public void updateOrderStatus(Long orderId, String status) {
        String url = orderServiceBaseUrl + "/orders/" + orderId + "/status";
        restTemplate.put(url, new OrderStatusUpdateRequest(status));
    }
}
```

- Uses **constructor injection** for both `RestTemplate` and the base URL.
- The base URL (`http://localhost:8081`) is configurable via properties, making it easy to point to different environments.
- Both methods use SLF4J logging for traceability.

---

## 8. Service Layer

### 8.1 PaymentService.java

**📁 Path:** `com.urbanvogue.payment.service.PaymentService`

**Why it exists:**
This is the **brain of the microservice** — it orchestrates the entire payment flow. All business logic lives here, keeping the controller thin and the entity focused on data integrity.

**What it does — the 9-step payment flow:**

```
processPayment(request, idempotencyKey)
```

| Step | What Happens | Why |
|---|---|---|
| **1. Idempotency check** | Look up `idempotencyKey` in DB. If found, return cached response. If the previous order update failed, retry it. | Prevents duplicate charges if the client retries |
| **2. Fetch order** | Call `OrderClient.getOrder(orderId)` | Get the order's amount, status, and user info |
| **3. Validate order** | Check: status = `"CREATED"`, amount > 0 | Can't pay for a cancelled/completed order or a zero-amount order |
| **4. Duplicate check** | `existsByOrderIdAndStatus(orderId, SUCCESS)` | Prevent paying for the same order twice |
| **5. Create payment** | `paymentMapper.toEntity(...)` → entity in INITIATED state | Record the payment attempt |
| **6. Simulate** | `paymentSimulator.simulate()` → SUCCESS or FAILED w/ reason | Mimics a real payment gateway call |
| **7. Mark outcome** | `payment.markSuccess(txnId)` or `payment.markFailed(reason)` | Transition the entity to its final state |
| **8. Persist** | `paymentRepository.save(payment)` | Store the result in the database |
| **9. Update order** | Call `OrderClient.updateOrderStatus(...)` in try-catch | Tell the Order Service the payment result — but **never fail the API** if this call fails |

**Critical design decisions:**

- **`@Transactional`** — the entire `processPayment` method runs in a single database transaction. If anything fails before step 8, nothing is saved.
- **Non-blocking order update** — Step 9 is wrapped in try-catch. If the Order Service is down, the payment still succeeds/fails correctly. The `orderUpdated` flag tracks whether notification succeeded, and it's retried on the next idempotent call (Step 1).
- **Constructor injection** — all 4 dependencies (`PaymentRepository`, `PaymentMapper`, `PaymentSimulator`, `OrderClient`) are injected via the constructor, making the class easily testable with mocks.

**`getPaymentByOrderId(orderId)`:**
Simple lookup — finds the payment by orderId or throws `PaymentNotFoundException`.

---

## 9. Exception Handling

### 9.1 PaymentNotFoundException.java

**📁 Path:** `com.urbanvogue.payment.exception.PaymentNotFoundException`

**Why it exists:**
Thrown when a `GET /payments/order/{orderId}` request finds no matching payment. Extending `RuntimeException` means it's an unchecked exception — no need for explicit `throws` declarations.

```java
public class PaymentNotFoundException extends RuntimeException {
    public PaymentNotFoundException(String message) { super(message); }
}
```

Mapped to **HTTP 404 Not Found** by `GlobalExceptionHandler`.

---

### 9.2 InvalidPaymentException.java

**📁 Path:** `com.urbanvogue.payment.exception.InvalidPaymentException`

**Why it exists:**
Thrown when a payment cannot be processed due to business rule violations:
- Order is not in `CREATED` status
- Order amount ≤ 0
- A successful payment already exists for the order

```java
public class InvalidPaymentException extends RuntimeException {
    public InvalidPaymentException(String message) { super(message); }
}
```

Mapped to **HTTP 400 Bad Request** by `GlobalExceptionHandler`.

---

### 9.3 GlobalExceptionHandler.java

**📁 Path:** `com.urbanvogue.payment.exception.GlobalExceptionHandler`

**Why it exists:**
Without a global handler, Spring returns inconsistent error formats. This class ensures every error — validation, business, external, or unexpected — returns a **uniform JSON structure**.

**What it handles:**

| Exception Type | HTTP Status | When It's Thrown |
|---|---|---|
| `PaymentNotFoundException` | 404 Not Found | No payment found for the given order |
| `InvalidPaymentException` | 400 Bad Request | Business rule violation |
| `MethodArgumentNotValidException` | 400 Bad Request | `@NotNull` / `@Valid` violations on request body |
| `MissingRequestHeaderException` | 400 Bad Request | Client forgot `Idempotency-Key` header |
| `RestClientException` | 503 Service Unavailable | Order Service is unreachable |
| `Exception` (catch-all) | 500 Internal Server Error | Anything unexpected |

**Response format (all errors):**

```json
{
    "timestamp": "2026-03-23T23:00:00",
    "status": 400,
    "error": "Bad Request",
    "message": "orderId: Order ID is required"
}
```

---

## 10. Controller Layer

### 10.1 PaymentController.java

**📁 Path:** `com.urbanvogue.payment.controller.PaymentController`

**Why it exists:**
The controller is the **entry point** for all HTTP requests. It's intentionally thin — it only handles request/response mechanics and delegates all business logic to `PaymentService`.

**What it does:**

**POST /payments** — Process a payment

```java
@PostMapping
public ResponseEntity<PaymentResponse> processPayment(
        @Valid @RequestBody PaymentRequest request,
        @RequestHeader("Idempotency-Key") String idempotencyKey) {

    PaymentResponse response = paymentService.processPayment(request, idempotencyKey);
    return new ResponseEntity<>(response, HttpStatus.CREATED);
}
```

- `@Valid` — triggers Jakarta validation on `PaymentRequest` (checks `@NotNull` fields)
- `@RequestHeader("Idempotency-Key")` — makes the header mandatory. If missing, Spring throws `MissingRequestHeaderException` (caught by `GlobalExceptionHandler`)
- Returns **HTTP 201 Created** with the payment response body

**GET /payments/order/{orderId}** — Retrieve a payment

```java
@GetMapping("/order/{orderId}")
public ResponseEntity<PaymentResponse> getPaymentByOrderId(@PathVariable Long orderId) {
    PaymentResponse response = paymentService.getPaymentByOrderId(orderId);
    return ResponseEntity.ok(response);
}
```

- `@PathVariable` — extracts `orderId` from the URL
- Returns **HTTP 200 OK** with the payment response body

---

## Architecture Summary

```
Client Request
     │
     ▼
┌─────────────────────┐
│  PaymentController   │  ← Thin layer: validation + delegation
│  POST /payments      │
│  GET /payments/...   │
└────────┬────────────┘
         │
         ▼
┌─────────────────────┐     ┌──────────────────┐
│  PaymentService      │────▶│  OrderClient      │ ──▶ Order Service
│  (9-step flow)       │     │  (RestTemplate)   │     (HTTP)
│                      │     └──────────────────┘
│                      │     ┌──────────────────┐
│                      │────▶│  PaymentSimulator │
│                      │     │  (random outcome) │
└────────┬────────────┘     └──────────────────┘
         │
         ▼
┌─────────────────────┐
│  PaymentRepository   │  ← Spring Data JPA
└────────┬────────────┘
         │
         ▼
┌─────────────────────┐
│  Payment Entity      │  ← H2 Database
│  (payments table)    │
└─────────────────────┘
```

---

## Quick Reference: How to Run

```bash
cd payment-service
mvn clean compile spring-boot:run
```

**Test payment:**
```bash
curl -X POST http://localhost:8082/payments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: test-001" \
  -d '{"orderId": 1, "paymentMethod": "UPI"}'
```

**Retrieve payment:**
```bash
curl http://localhost:8082/payments/order/1
```

**H2 Console:** `http://localhost:8082/h2-console` (JDBC URL: `jdbc:h2:file:./data/payment`)
