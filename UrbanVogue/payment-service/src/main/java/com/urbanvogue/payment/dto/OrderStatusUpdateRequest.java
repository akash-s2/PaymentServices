package com.urbanvogue.payment.dto;

/**
 * Request body sent to the Order Service when updating order status.
 */
public record OrderStatusUpdateRequest(
        String status
) {
}
