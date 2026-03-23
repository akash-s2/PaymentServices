package com.urbanvogue.payment.dto;

import com.urbanvogue.payment.entity.PaymentMethod;
import com.urbanvogue.payment.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Immutable response DTO carrying the full payment state back to the client.
 */
public record PaymentResponse(
        Long id,
        Long orderId,
        BigDecimal amount,
        PaymentStatus status,
        PaymentMethod method,
        String transactionId,
        String idempotencyKey,
        boolean orderUpdated,
        String failureReason,
        LocalDateTime createdAt
) {
}
