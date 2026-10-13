package com.ecommerce.order.dto;

import java.math.BigDecimal;

public record OrderItemResponse(
        Long productId, String productName, String sku, BigDecimal unitPrice, int quantity, BigDecimal lineTotal) {
}
