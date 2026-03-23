package com.urbanvogue.payment.mapper;

import com.urbanvogue.payment.dto.OrderResponse;
import com.urbanvogue.payment.dto.PaymentRequest;
import com.urbanvogue.payment.dto.PaymentResponse;
import com.urbanvogue.payment.entity.Payment;
import org.springframework.stereotype.Component;

/**
 * Maps between Payment entities and DTOs.
 *
 * <p>Keeps conversion logic centralised — controllers and services
 * never construct DTOs or entities directly.</p>
 */
@Component
public class PaymentMapper {

    /**
     * Converts a Payment entity to an immutable response DTO.
     */
    public PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getOrderId(),
                payment.getAmount(),
                payment.getStatus(),
                payment.getMethod(),
                payment.getTransactionId(),
                payment.getIdempotencyKey(),
                payment.isOrderUpdated(),
                payment.getFailureReason(),
                payment.getCreatedAt()
        );
    }

    /**
     * Constructs a new Payment entity from the incoming request,
     * order data (for the amount), and the idempotency key.
     *
     * <p>The entity starts in INITIATED status by default.</p>
     */
    public Payment toEntity(PaymentRequest request,
                            OrderResponse order,
                            String idempotencyKey) {
        return new Payment(
                request.orderId(),
                order.totalAmount(),
                request.paymentMethod(),
                idempotencyKey
        );
    }
}
