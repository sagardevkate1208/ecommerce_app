package com.ecommerce.catalog.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record ProductResponse(
        Long id, String name, String description, String sku, BigDecimal price,
        Long categoryId, String categoryName, String imageUrl, boolean active, Instant createdAt) {
}
