package com.ecommerce.cart.dto;

import java.math.BigDecimal;

public record CartItemResponse(
        Long productId, String productName, String sku, String imageUrl,
        BigDecimal unitPrice, int quantity, BigDecimal lineTotal) {
}
