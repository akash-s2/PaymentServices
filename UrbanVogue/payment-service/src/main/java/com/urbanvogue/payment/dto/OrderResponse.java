package com.urbanvogue.payment.dto;

import java.math.BigDecimal;

/**
 * Represents an order fetched from the Order Service.
 */
public record OrderResponse(
        Long orderId,
        Long userId,
        BigDecimal totalAmount,
        String status
) {
}
