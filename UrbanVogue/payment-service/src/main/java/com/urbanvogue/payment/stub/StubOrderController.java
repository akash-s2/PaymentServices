package com.urbanvogue.payment.stub;

import com.urbanvogue.payment.dto.OrderResponse;
import com.urbanvogue.payment.dto.OrderStatusUpdateRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stub Order Service controller for local development and testing.
 *
 * <p>Only active when the {@code dev} profile is enabled.
 * Simulates the Order Service endpoints that {@link com.urbanvogue.payment.client.OrderClient}
 * calls, so the payment flow can be tested end-to-end without a real Order Service.</p>
 *
 * <p>Start with: {@code mvn spring-boot:run -Dspring-boot.run.profiles=dev}</p>
 */
@RestController
@RequestMapping("/orders")
@Profile("dev")
public class StubOrderController {

    private static final Logger log = LoggerFactory.getLogger(StubOrderController.class);

    /**
     * In-memory store tracking order statuses.
     * All orders start as CREATED; payment processing updates them.
     */
    private final Map<Long, String> orderStatuses = new ConcurrentHashMap<>();

    /**
     * GET /orders/{orderId} — Returns a fake order.
     *
     * <p>Simulates different scenarios based on orderId ranges:</p>
     * <ul>
     *   <li>1–99: Normal orders (CREATED, amount = orderId × 100)</li>
     *   <li>100–199: Already-paid orders (PAID)</li>
     *   <li>200+: Invalid amount orders (amount = 0)</li>
     * </ul>
     */
    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable Long orderId) {
        log.info("[STUB] GET /orders/{}", orderId);

        String status;
        BigDecimal amount;

        if (orderId >= 200) {
            // Scenario: order with zero amount — should trigger validation error
            status = "CREATED";
            amount = BigDecimal.ZERO;
        } else if (orderId >= 100) {
            // Scenario: order already paid — should trigger "not in CREATED status" error
            status = "PAID";
            amount = new BigDecimal(orderId * 100);
        } else {
            // Normal scenario: valid order ready for payment
            status = orderStatuses.getOrDefault(orderId, "CREATED");
            amount = new BigDecimal(orderId * 100);
        }

        OrderResponse response = new OrderResponse(orderId, 1L, amount, status);
        log.info("[STUB] Returning order: id={}, status={}, amount={}", orderId, status, amount);
        return ResponseEntity.ok(response);
    }

    /**
     * PUT /orders/{orderId}/status — Updates the stub order's status.
     *
     * <p>Tracks status changes in memory so subsequent GET calls
     * reflect the updated status (e.g., PAID after successful payment).</p>
     */
    @PutMapping("/{orderId}/status")
    public ResponseEntity<Void> updateOrderStatus(
            @PathVariable Long orderId,
            @RequestBody OrderStatusUpdateRequest request) {

        log.info("[STUB] PUT /orders/{}/status — new status: {}", orderId, request.status());
        orderStatuses.put(orderId, request.status());
        return ResponseEntity.ok().build();
    }
}
