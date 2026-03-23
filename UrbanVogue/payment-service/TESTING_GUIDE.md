# UrbanVogue Payment Service — Testing Guide

This guide explains how to test the payment microservice **without** the real Order Service running, using the built-in stub.

---

## Step 1: Start the Application with the `dev` Profile

The `dev` profile activates a **StubOrderController** that simulates the Order Service on the same port.

**Using Maven:**
```bash
cd payment-service
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

**Using IntelliJ IDEA:**
1. Open Run/Debug Configuration
2. Add VM option: `-Dspring.profiles.active=dev`
3. Or set Active Profile to `dev` in the Spring Boot run config

You should see in the logs:
```
Started PaymentServiceApplication with profile(s): [dev]
```

---

## Step 2: Verify the Stub is Active

Call the stub order endpoint:
```bash
curl http://localhost:8082/orders/1
```

**Expected response:**
```json
{
    "orderId": 1,
    "userId": 1,
    "totalAmount": 100,
    "status": "CREATED"
}
```

If you get a 404, the `dev` profile was not activated.

---

## Step 3: Test Scenarios

### ✅ Test 1 — Successful Payment (Happy Path)

```bash
curl -X POST http://localhost:8082/payments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: test-001" \
  -d '{"orderId": 1, "paymentMethod": "UPI"}'
```

**Expected:** HTTP 201 with `status: SUCCESS` or `status: FAILED` (depends on the 80/20 simulator).

Sample success response:
```json
{
    "id": 1,
    "orderId": 1,
    "amount": 100,
    "status": "SUCCESS",
    "method": "UPI",
    "transactionId": "a3b8d1b6-...",
    "idempotencyKey": "test-001",
    "orderUpdated": true,
    "failureReason": null,
    "createdAt": "2026-03-24T00:30:00"
}
```

Sample failure response:
```json
{
    "id": 1,
    "orderId": 1,
    "amount": 100,
    "status": "FAILED",
    "method": "UPI",
    "transactionId": null,
    "idempotencyKey": "test-001",
    "orderUpdated": true,
    "failureReason": "INSUFFICIENT_FUNDS",
    "createdAt": "2026-03-24T00:30:00"
}
```

---

### 🔁 Test 2 — Idempotency (Same Key = Same Response)

Run the **exact same curl** from Test 1 again:
```bash
curl -X POST http://localhost:8082/payments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: test-001" \
  -d '{"orderId": 1, "paymentMethod": "UPI"}'
```

**Expected:** HTTP 201 with the **exact same response** as Test 1 (cached). No new payment created.

Check logs — you should see:
```
Idempotency key test-001 already exists — returning cached response
```

---

### 🔁 Test 3 — Different Idempotency Key, Same Order

```bash
curl -X POST http://localhost:8082/payments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: test-002" \
  -d '{"orderId": 1, "paymentMethod": "CREDIT_CARD"}'
```

**Expected:** HTTP 400 — either:
- `"Order 1 is not in CREATED status"` (if Test 1 succeeded, order is now PAID)
- `"A successful payment already exists for order 1"` (duplicate SUCCESS check)

---

### 📋 Test 4 — Retrieve Payment by Order

```bash
curl http://localhost:8082/payments/order/1
```

**Expected:** HTTP 200 with the payment details from Test 1.

---

### ❌ Test 5 — Missing Idempotency Key Header

```bash
curl -X POST http://localhost:8082/payments \
  -H "Content-Type: application/json" \
  -d '{"orderId": 2, "paymentMethod": "UPI"}'
```

**Expected:** HTTP 400
```json
{
    "timestamp": "...",
    "status": 400,
    "error": "Bad Request",
    "message": "Missing required header: Idempotency-Key"
}
```

---

### ❌ Test 6 — Validation Error (Missing Fields)

```bash
curl -X POST http://localhost:8082/payments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: test-003" \
  -d '{}'
```

**Expected:** HTTP 400
```json
{
    "timestamp": "...",
    "status": 400,
    "error": "Bad Request",
    "message": "orderId: Order ID is required; paymentMethod: Payment method is required"
}
```

---

### ❌ Test 7 — Already Paid Order (orderId 100–199)

The stub returns `status: PAID` for orders in the 100–199 range:

```bash
curl -X POST http://localhost:8082/payments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: test-004" \
  -d '{"orderId": 100, "paymentMethod": "DEBIT_CARD"}'
```

**Expected:** HTTP 400
```json
{
    "message": "Order 100 is not in CREATED status (current: PAID)"
}
```

---

### ❌ Test 8 — Zero Amount Order (orderId 200+)

The stub returns `amount: 0` for orders 200+:

```bash
curl -X POST http://localhost:8082/payments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: test-005" \
  -d '{"orderId": 200, "paymentMethod": "WALLET"}'
```

**Expected:** HTTP 400
```json
{
    "message": "Order 200 has invalid amount: 0"
}
```

---

### 🔍 Test 9 — Payment Not Found

```bash
curl http://localhost:8082/payments/order/9999
```

**Expected:** HTTP 404
```json
{
    "message": "No payment found for order 9999"
}
```

---

### 💳 Test 10 — Try Different Payment Methods

```bash
# UPI
curl -X POST http://localhost:8082/payments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: method-upi" \
  -d '{"orderId": 10, "paymentMethod": "UPI"}'

# Credit Card
curl -X POST http://localhost:8082/payments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: method-cc" \
  -d '{"orderId": 11, "paymentMethod": "CREDIT_CARD"}'

# Net Banking
curl -X POST http://localhost:8082/payments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: method-nb" \
  -d '{"orderId": 12, "paymentMethod": "NET_BANKING"}'
```

---

## Step 4: Inspect the Database (H2 Console)

1. Open your browser: **http://localhost:8082/h2-console**
2. JDBC URL: `jdbc:h2:file:./data/payment`
3. Username: `sa`
4. Password: *(leave empty)*
5. Click **Connect**

**Useful queries:**
```sql
-- View all payments
SELECT * FROM PAYMENTS;

-- View only successful payments
SELECT * FROM PAYMENTS WHERE STATUS = 'SUCCESS';

-- View failed payments with reasons
SELECT ORDER_ID, STATUS, FAILURE_REASON, CREATED_AT
FROM PAYMENTS WHERE STATUS = 'FAILED';

-- Check idempotency keys
SELECT IDEMPOTENCY_KEY, ORDER_ID, STATUS FROM PAYMENTS;
```

---

## Stub Order Controller — How It Works

The stub (`StubOrderController.java`) simulates different scenarios based on **orderId ranges**:

| Order ID Range | Status Returned | Amount Returned | Test Scenario |
|---|---|---|---|
| **1 – 99** | `CREATED` | orderId × 100 | ✅ Normal orders, ready for payment |
| **100 – 199** | `PAID` | orderId × 100 | ❌ Already-paid, triggers validation error |
| **200+** | `CREATED` | `0` | ❌ Zero amount, triggers validation error |

The stub also **remembers status updates** — so after a successful payment for order 1, calling `GET /orders/1` again returns `PAID` instead of `CREATED`.

---

## Quick Reset

To reset all data and start fresh:
1. Stop the application
2. Delete the `data/` folder in `payment-service/`
3. Restart with `mvn spring-boot:run -Dspring-boot.run.profiles=dev`
