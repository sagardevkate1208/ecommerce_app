package com.ecommerce.order.dto;

import java.util.List;

/** The subset of Cart's response that checkout needs. */
public record CartInfo(List<CartItemInfo> items) {

    public record CartItemInfo(Long productId, int quantity) {
    }
}
