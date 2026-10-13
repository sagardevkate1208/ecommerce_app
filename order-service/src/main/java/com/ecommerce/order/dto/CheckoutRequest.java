package com.ecommerce.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record CheckoutRequest(@NotNull @Valid ShippingAddress shippingAddress) {
}
