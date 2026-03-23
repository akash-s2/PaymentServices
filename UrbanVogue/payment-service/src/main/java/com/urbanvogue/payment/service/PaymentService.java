package com.urbanvogue.payment.service;

import com.urbanvogue.payment.client.OrderClient;
import com.urbanvogue.payment.dto.OrderResponse;
import com.urbanvogue.payment.dto.PaymentRequest;
import com.urbanvogue.payment.dto.PaymentResponse;
import com.urbanvogue.payment.entity.Payment;
import com.urbanvogue.payment.entity.PaymentStatus;
import com.urbanvogue.payment.exception.InvalidPaymentException;
import com.urbanvogue.payment.exception.PaymentNotFoundException;
import com.urbanvogue.payment.mapper.PaymentMapper;
import com.urbanvogue.payment.repository.PaymentRepository;
import com.urbanvogue.payment.simulator.PaymentSimulationResult;
import com.urbanvogue.payment.simulator.PaymentSimulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Core service implementing the full payment processing flow with
 * idempotency, order validation, simulated payment execution,
 * and order service notification.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepository;
    private final PaymentMapper paymentMapper;
    private final PaymentSimulator paymentSimulator;
    private final OrderClient orderClient;

    public PaymentService(PaymentRepository paymentRepository,
                          PaymentMapper paymentMapper,
                          PaymentSimulator paymentSimulator,
                          OrderClient orderClient) {
        this.paymentRepository = paymentRepository;
        this.paymentMapper = paymentMapper;
        this.paymentSimulator = paymentSimulator;
        this.orderClient = orderClient;
    }

    // ====================================================================
    // PUBLIC API
    // ====================================================================

    /**
     * Processes a payment for the given request and idempotency key.
     *
     * <p><b>Flow:</b></p>
     * <ol>
     *   <li>Idempotency check — return existing payment if key already used</li>
     *   <li>Fetch order from Order Service</li>
     *   <li>Validate order (status = CREATED, amount &gt; 0)</li>
     *   <li>Prevent duplicate SUCCESS payments for the same orderId</li>
     *   <li>Create INITIATED payment</li>
     *   <li>Simulate payment gateway</li>
     *   <li>Mark SUCCESS (with txnId) or FAILED (with reason)</li>
     *   <li>Persist payment</li>
     *   <li>Notify Order Service (non-blocking)</li>
     * </ol>
     */
    @Transactional
    public PaymentResponse processPayment(PaymentRequest request, String idempotencyKey) {
        // ---- Step 1: Idempotency ----
        Optional<Payment> existing = paymentRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            log.info("Idempotency key {} already exists — returning cached response", idempotencyKey);
            Payment cachedPayment = existing.get();

            // Retry order update if it wasn't acknowledged last time
            if (!cachedPayment.isOrderUpdated()) {
                retryOrderUpdate(cachedPayment);
            }

            return paymentMapper.toResponse(cachedPayment);
        }

        // ---- Step 2: Fetch order ----
        OrderResponse order = orderClient.getOrder(request.orderId());
        log.info("Fetched order {}: status={}, amount={}", order.orderId(), order.status(), order.totalAmount());

        // ---- Step 3: Validate order ----
        validateOrder(order);

        // ---- Step 4: Prevent duplicate SUCCESS payment ----
        if (paymentRepository.existsByOrderIdAndStatus(request.orderId(), PaymentStatus.SUCCESS)) {
            throw new InvalidPaymentException(
                    "A successful payment already exists for order " + request.orderId());
        }

        // ---- Step 5: Create INITIATED payment ----
        Payment payment = paymentMapper.toEntity(request, order, idempotencyKey);
        log.info("Created INITIATED payment for order {}", request.orderId());

        // ---- Step 6 & 7: Simulate and mark outcome ----
        PaymentSimulationResult result = paymentSimulator.simulate();

        if (result.success()) {
            String transactionId = UUID.randomUUID().toString();
            payment.markSuccess(transactionId);
            log.info("Payment SUCCEEDED — txnId={}", transactionId);
        } else {
            payment.markFailed(result.failureReason().name());
            log.info("Payment FAILED — reason={}", result.failureReason());
        }

        // ---- Step 8: Persist ----
        payment = paymentRepository.save(payment);
        log.info("Payment {} saved with status {}", payment.getId(), payment.getStatus());

        // ---- Step 9: Update Order Service (non-blocking) ----
        updateOrderService(payment);

        return paymentMapper.toResponse(payment);
    }

    /**
     * Retrieves the payment associated with the given orderId.
     */
    public PaymentResponse getPaymentByOrderId(Long orderId) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new PaymentNotFoundException(
                        "No payment found for order " + orderId));

        return paymentMapper.toResponse(payment);
    }

    // ====================================================================
    // PRIVATE HELPERS
    // ====================================================================

    /**
     * Validates the fetched order:
     * <ul>
     *   <li>Order status must be CREATED</li>
     *   <li>Total amount must be greater than zero</li>
     * </ul>
     */
    private void validateOrder(OrderResponse order) {
        if (!"CREATED".equals(order.status())) {
            throw new InvalidPaymentException(
                    "Order " + order.orderId() + " is not in CREATED status (current: " + order.status() + ")");
        }
        if (order.totalAmount() == null || order.totalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidPaymentException(
                    "Order " + order.orderId() + " has invalid amount: " + order.totalAmount());
        }
    }

    /**
     * Notifies the Order Service about the payment outcome.
     * Wrapped in try-catch so that a failure here does NOT fail the API response.
     */
    private void updateOrderService(Payment payment) {
        try {
            String newStatus = payment.getStatus() == PaymentStatus.SUCCESS ? "PAID" : "PAYMENT_FAILED";
            orderClient.updateOrderStatus(payment.getOrderId(), newStatus);
            payment.markOrderUpdated();
            paymentRepository.save(payment);
            log.info("Order {} status updated to {}", payment.getOrderId(), newStatus);
        } catch (Exception ex) {
            log.error("Failed to update Order Service for order {} — will retry on next idempotent call",
                    payment.getOrderId(), ex);
        }
    }

    /**
     * Retries the order update for an already-processed payment
     * whose order-service notification previously failed.
     */
    private void retryOrderUpdate(Payment payment) {
        log.info("Retrying order update for payment {} (order {})", payment.getId(), payment.getOrderId());
        updateOrderService(payment);
    }
}
