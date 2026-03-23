# UrbanVogue Payment Service — IntelliJ + Postman Testing Guide

---

## Part 1: Running the Application from IntelliJ IDEA

### Step 1: Open the Project

1. Open IntelliJ IDEA
2. **File → Open** → navigate to `payment-service` folder → click **Open**
3. Wait for IntelliJ to import the Maven project and download dependencies (bottom progress bar)

### Step 2: Set the `dev` Profile

The `dev` profile activates the stub Order Service so you can test without running a separate service.

1. Open `PaymentServiceApplication.java`
   ```
   src/main/java/com/urbanvogue/payment/PaymentServiceApplication.java
   ```
2. You'll see the green ▶️ play button next to `public static void main`
3. **Before clicking it**, configure the dev profile:

**Option A — Edit Run Configuration (Recommended):**
1. Click the **dropdown** next to the Run/Debug button in the toolbar (top-right)
2. Click **Edit Configurations...**
3. Click **+** → **Spring Boot** (or select the existing `PaymentServiceApplication` config)
4. Set:
   - **Main class:** `com.urbanvogue.payment.PaymentServiceApplication`
   - **Active profiles:** `dev`
5. Click **Apply** → **OK**

**Option B — Add VM Option:**
1. Same Edit Configurations dialog
2. In **VM options** field, add:
   ```
   -Dspring.profiles.active=dev
   ```
3. Click **Apply** → **OK**

**Option C — Add to application.properties (quickest, but less clean):**
Add this line at the top of `application.properties`:
```properties
spring.profiles.active=dev
```
> ⚠️ Remove this line before production deployment!

### Step 3: Run the Application

1. Click the green ▶️ **Run** button next to `main()` in `PaymentServiceApplication.java`
2. Wait for the console to show:
   ```
   Started PaymentServiceApplication in X.XX seconds
   ```
3. Confirm you see the `dev` profile:
   ```
   The following 1 profile is active: "dev"
   ```

The app is now running at **http://localhost:8082**.

---

## Part 2: Testing with Postman

Open Postman and create a new **Collection** called `UrbanVogue Payment Service`. Add the following requests:

---

### Test 1: ✅ Verify Stub is Active

| Field | Value |
|---|---|
| **Method** | `GET` |
| **URL** | `http://localhost:8082/orders/1` |

Click **Send**.

**Expected:** Status `200 OK`
```json
{
    "orderId": 1,
    "userId": 1,
    "totalAmount": 100,
    "status": "CREATED"
}
```

---

### Test 2: ✅ Process a Payment (Happy Path)

| Field | Value |
|---|---|
| **Method** | `POST` |
| **URL** | `http://localhost:8082/payments` |

**Headers tab** — add:

| Key | Value |
|---|---|
| `Content-Type` | `application/json` |
| `Idempotency-Key` | `postman-test-001` |

**Body tab** — select `raw` → `JSON`:
```json
{
    "orderId": 1,
    "paymentMethod": "UPI"
}
```

Click **Send**.

**Expected:** Status `201 Created`
```json
{
    "id": 1,
    "orderId": 1,
    "amount": 100,
    "status": "SUCCESS",
    "method": "UPI",
    "transactionId": "uuid-string-here",
    "idempotencyKey": "postman-test-001",
    "orderUpdated": true,
    "failureReason": null,
    "createdAt": "2026-03-24T00:30:00"
}
```
> **Note:** `status` may be `FAILED` with a `failureReason` — the simulator has a 20% failure rate. That's expected!

---

### Test 3: 🔁 Idempotency Check (Replay Same Request)

Send the **exact same request** from Test 2 again (same `Idempotency-Key: postman-test-001`).

**Expected:** Status `201 Created` with the **exact same response** — no new payment created. Check the IntelliJ console for:
```
Idempotency key postman-test-001 already exists — returning cached response
```

---

### Test 4: 📋 Get Payment by Order ID

| Field | Value |
|---|---|
| **Method** | `GET` |
| **URL** | `http://localhost:8082/payments/order/1` |

Click **Send**.

**Expected:** Status `200 OK` with the same payment from Test 2.

---

### Test 5: ❌ Missing Idempotency-Key Header

| Field | Value |
|---|---|
| **Method** | `POST` |
| **URL** | `http://localhost:8082/payments` |

**Headers:** Only `Content-Type: application/json` (no `Idempotency-Key`)

**Body:**
```json
{
    "orderId": 2,
    "paymentMethod": "UPI"
}
```

**Expected:** Status `400 Bad Request`
```json
{
    "status": 400,
    "error": "Bad Request",
    "message": "Missing required header: Idempotency-Key"
}
```

---

### Test 6: ❌ Validation Error (Empty Body)

| Field | Value |
|---|---|
| **Method** | `POST` |
| **URL** | `http://localhost:8082/payments` |

**Headers:** `Content-Type: application/json` + `Idempotency-Key: postman-test-006`

**Body:**
```json
{}
```

**Expected:** Status `400 Bad Request`
```json
{
    "status": 400,
    "error": "Bad Request",
    "message": "orderId: Order ID is required; paymentMethod: Payment method is required"
}
```

---

### Test 7: ❌ Already Paid Order

| Field | Value |
|---|---|
| **Method** | `POST` |
| **URL** | `http://localhost:8082/payments` |

**Headers:** `Content-Type: application/json` + `Idempotency-Key: postman-test-007`

**Body:**
```json
{
    "orderId": 100,
    "paymentMethod": "DEBIT_CARD"
}
```

**Expected:** Status `400 Bad Request`
```json
{
    "message": "Order 100 is not in CREATED status (current: PAID)"
}
```

---

### Test 8: ❌ Zero Amount Order

| Field | Value |
|---|---|
| **Method** | `POST` |
| **URL** | `http://localhost:8082/payments` |

**Headers:** `Content-Type: application/json` + `Idempotency-Key: postman-test-008`

**Body:**
```json
{
    "orderId": 200,
    "paymentMethod": "WALLET"
}
```

**Expected:** Status `400 Bad Request`
```json
{
    "message": "Order 200 has invalid amount: 0"
}
```

---

### Test 9: ❌ Payment Not Found

| Field | Value |
|---|---|
| **Method** | `GET` |
| **URL** | `http://localhost:8082/payments/order/9999` |

**Expected:** Status `404 Not Found`
```json
{
    "message": "No payment found for order 9999"
}
```

---

### Test 10: 💳 Multiple Payment Methods

Create three separate requests:

**UPI Payment:**
- URL: `POST http://localhost:8082/payments`
- Headers: `Idempotency-Key: method-upi`
- Body: `{"orderId": 10, "paymentMethod": "UPI"}`

**Credit Card Payment:**
- URL: `POST http://localhost:8082/payments`
- Headers: `Idempotency-Key: method-cc`
- Body: `{"orderId": 11, "paymentMethod": "CREDIT_CARD"}`

**Net Banking Payment:**
- URL: `POST http://localhost:8082/payments`
- Headers: `Idempotency-Key: method-nb`
- Body: `{"orderId": 12, "paymentMethod": "NET_BANKING"}`

---

## Part 3: Inspect the Database

1. Open browser: **http://localhost:8082/h2-console**
2. Enter:
   - **JDBC URL:** `jdbc:h2:file:./data/payment`
   - **Username:** `sa`
   - **Password:** *(leave empty)*
3. Click **Connect**

**Useful queries:**
```sql
-- All payments
SELECT * FROM PAYMENTS;

-- Only failures with reasons
SELECT ORDER_ID, STATUS, FAILURE_REASON FROM PAYMENTS WHERE STATUS = 'FAILED';
```

---

## Part 4: Reset Data for Fresh Testing

1. **Stop** the application in IntelliJ (click the red ⬛ stop button)
2. Delete the `data/` folder inside `payment-service/`
3. **Run** the application again

All payments are cleared and you can start fresh.

---

## Stub Order ID Cheat Sheet

| Order ID | Stub Behavior | Use For Testing |
|---|---|---|
| `1 – 99` | CREATED, amount = orderId × 100 | ✅ Normal payments |
| `100 – 199` | PAID, amount = orderId × 100 | ❌ "Not in CREATED status" error |
| `200+` | CREATED, amount = 0 | ❌ "Invalid amount" error |
