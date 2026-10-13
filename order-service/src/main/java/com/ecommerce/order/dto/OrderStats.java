package com.ecommerce.order.dto;

import com.ecommerce.order.entity.OrderStatus;
import java.math.BigDecimal;
import java.util.Map;

/** Dashboard numbers. Revenue counts orders that are paid (PAID, SHIPPED, DELIVERED); cancelled ones are excluded. */
public record OrderStats(Map<OrderStatus, Long> countByStatus, long totalOrders, BigDecimal revenue) {
}
