package com.ecommerce.cart.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateItemRequest(@NotNull @Min(1) @Max(99) Integer quantity) {
}
