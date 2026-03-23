package com.urbanvogue.payment.client;

import com.urbanvogue.payment.dto.OrderResponse;
import com.urbanvogue.payment.dto.OrderStatusUpdateRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * HTTP client for communicating with the Order Service.
 *
 * <p>Uses constructor injection for both RestTemplate and the
 * configurable base URL.</p>
 */
@Component
public class OrderClient {

    private static final Logger log = LoggerFactory.getLogger(OrderClient.class);

    private final RestTemplate restTemplate;
    private final String orderServiceBaseUrl;

    public OrderClient(RestTemplate restTemplate,
                       @Value("${order.service.base-url}") String orderServiceBaseUrl) {
        this.restTemplate = restTemplate;
        this.orderServiceBaseUrl = orderServiceBaseUrl;
    }

    /**
     * Fetches an order from the Order Service.
     *
     * @param orderId the order to fetch
     * @return the order data
     * @throws org.springframework.web.client.RestClientException on HTTP errors
     */
    public OrderResponse getOrder(Long orderId) {
        String url = orderServiceBaseUrl + "/orders/" + orderId;
        log.info("Fetching order from Order Service: {}", url);
        return restTemplate.getForObject(url, OrderResponse.class);
    }

    /**
     * Updates an order's status in the Order Service.
     *
     * @param orderId the order to update
     * @param status  the new status (e.g., PAID, PAYMENT_FAILED)
     */
    public void updateOrderStatus(Long orderId, String status) {
        String url = orderServiceBaseUrl + "/orders/" + orderId + "/status";
        log.info("Updating order {} status to {} via: {}", orderId, status, url);
        restTemplate.put(url, new OrderStatusUpdateRequest(status));
    }
}
