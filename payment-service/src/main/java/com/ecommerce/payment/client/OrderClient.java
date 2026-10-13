package com.ecommerce.payment.client;

import com.ecommerce.payment.dto.OrderInfo;
import com.ecommerce.payment.exception.ApiException;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/** Calls order-service: reads an order as the user, and changes its status with the shared internal key. */
@Component
public class OrderClient {

    private final RestClient restClient;
    private final String internalKey;

    public OrderClient(RestClient.Builder builder, @Value("${app.order.base-url}") String baseUrl,
                       @Value("${app.internal.api-key}") String internalKey) {
        this.restClient = builder.baseUrl(baseUrl).build();
        this.internalKey = internalKey;
    }

    /** Fetches the order using the user's own token, so Order itself enforces that it belongs to them. */
    public OrderInfo getOrder(String authorizationHeader, Long orderId) {
        try {
            return restClient.get().uri("/api/orders/{id}", orderId)
                    .header(HttpHeaders.AUTHORIZATION, authorizationHeader)
                    .retrieve().body(OrderInfo.class);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                throw new ApiException(HttpStatus.NOT_FOUND, "Order not found");
            }
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Order service returned an error");
        } catch (ResourceAccessException e) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Order service is unavailable");
        }
    }

    /** Fetches any order using an ADMIN's token (the customer endpoint would hide orders that are not the caller's). */
    public OrderInfo getOrderAsAdmin(String authorizationHeader, Long orderId) {
        try {
            return restClient.get().uri("/api/admin/orders/{id}", orderId)
                    .header(HttpHeaders.AUTHORIZATION, authorizationHeader)
                    .retrieve().body(OrderInfo.class);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                throw new ApiException(HttpStatus.NOT_FOUND, "Order not found");
            }
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Order service returned an error");
        } catch (ResourceAccessException e) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Order service is unavailable");
        }
    }

    /** Moves the order to a new status (for example PAID or CANCELLED). Throws 409 if Order refuses the change. */
    public void updateStatus(Long orderId, String status) {
        try {
            restClient.put().uri("/internal/orders/{id}/status", orderId)
                    .header("X-Internal-Key", internalKey)
                    .body(Map.of("status", status))
                    .retrieve().toBodilessEntity();
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == HttpStatus.CONFLICT.value()) {
                throw new ApiException(HttpStatus.CONFLICT, "Order cannot be changed to " + status
                        + " in its current state");
            }
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Order service returned an error");
        } catch (ResourceAccessException e) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Order service is unavailable");
        }
    }
}
