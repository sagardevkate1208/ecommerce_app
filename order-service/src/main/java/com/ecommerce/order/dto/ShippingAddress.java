package com.ecommerce.order.dto;

import jakarta.validation.constraints.NotBlank;

public record ShippingAddress(
        @NotBlank String recipientName,
        String phone,
        @NotBlank String line1,
        String line2,
        @NotBlank String city,
        String state,
        @NotBlank String postalCode,
        @NotBlank String country) {
}
