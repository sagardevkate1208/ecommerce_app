package com.ecommerce.order.dto;

import java.math.BigDecimal;

/** The subset of Catalog's product response that the order needs. */
public record ProductInfo(Long id, String name, String sku, BigDecimal price, boolean active) {
}
