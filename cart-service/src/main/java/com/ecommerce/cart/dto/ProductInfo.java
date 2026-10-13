package com.ecommerce.cart.dto;

import java.math.BigDecimal;

/** The subset of Catalog's product response that the cart needs. */
public record ProductInfo(Long id, String name, String sku, BigDecimal price, String imageUrl, boolean active) {
}
