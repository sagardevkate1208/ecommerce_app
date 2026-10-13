package com.ecommerce.order.dto;

import com.ecommerce.order.entity.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
        Long id, Long userId, OrderStatus status, BigDecimal totalAmount, ShippingAddress shippingAddress,
        List<OrderItemResponse> items, Instant createdAt, Instant updatedAt) {
}
