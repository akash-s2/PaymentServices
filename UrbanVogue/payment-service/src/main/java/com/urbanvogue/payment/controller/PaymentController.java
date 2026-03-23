package com.urbanvogue.payment.controller;

import com.urbanvogue.payment.dto.PaymentRequest;
import com.urbanvogue.payment.dto.PaymentResponse;
import com.urbanvogue.payment.service.PaymentService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller exposing payment endpoints.
 */
@RestController
@RequestMapping("/payments")
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /**
     * Initiates a payment. Requires an {@code Idempotency-Key} header
     * to ensure safe retries.
     *
     * @param request        the payment request body
     * @param idempotencyKey unique key for this payment attempt
     * @return the created/cached payment response
     */
    @PostMapping
    public ResponseEntity<PaymentResponse> processPayment(
            @Valid @RequestBody PaymentRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {

        log.info("POST /payments — orderId={}, method={}, idempotencyKey={}",
                request.orderId(), request.paymentMethod(), idempotencyKey);

        PaymentResponse response = paymentService.processPayment(request, idempotencyKey);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    /**
     * Retrieves the payment for a given order.
     *
     * @param orderId the order ID to look up
     * @return the payment associated with the order
     */
    @GetMapping("/order/{orderId}")
    public ResponseEntity<PaymentResponse> getPaymentByOrderId(@PathVariable Long orderId) {
        log.info("GET /payments/order/{}", orderId);

        PaymentResponse response = paymentService.getPaymentByOrderId(orderId);
        return ResponseEntity.ok(response);
    }
}
