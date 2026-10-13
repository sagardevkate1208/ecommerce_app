package com.ecommerce.order.controller;

import com.ecommerce.order.dto.OrderResponse;
import com.ecommerce.order.dto.StatusUpdateRequest;
import com.ecommerce.order.exception.ApiException;
import com.ecommerce.order.service.OrderService;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service endpoints (used by payment-service). They are open to Spring Security but guarded by a shared
 * key in the X-Internal-Key header. Never expose /internal/** through the API gateway.
 */
@RestController
@RequestMapping("/internal/orders")
public class InternalOrderController {

    private final OrderService orderService;
    private final byte[] apiKey;

    public InternalOrderController(OrderService orderService, @Value("${app.internal.api-key}") String apiKey) {
        this.orderService = orderService;
        this.apiKey = apiKey.getBytes(StandardCharsets.UTF_8);
    }

    @PutMapping("/{id}/status")
    public OrderResponse updateStatus(@RequestHeader(value = "X-Internal-Key", required = false) String key,
                                      @PathVariable Long id, @Valid @RequestBody StatusUpdateRequest request) {
        // Constant-time comparison so the key cannot be guessed byte by byte.
        if (key == null || !MessageDigest.isEqual(apiKey, key.getBytes(StandardCharsets.UTF_8))) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid internal key");
        }
        return orderService.adminUpdateStatus(id, request.status());
    }
}
