package com.urbanvogue.payment.dto;

import com.urbanvogue.payment.entity.PaymentMethod;
import jakarta.validation.constraints.NotNull;

/**
 * Immutable request DTO for initiating a payment.
 *
 * <p>Uses a Java record — no setters, no mutability, no Lombok.</p>
 */
public record PaymentRequest(

        @NotNull(message = "Order ID is required")
        Long orderId,

        @NotNull(message = "Payment method is required")
        PaymentMethod paymentMethod
) {
}
