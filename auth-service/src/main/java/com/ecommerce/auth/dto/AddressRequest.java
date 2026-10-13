package com.ecommerce.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record AddressRequest(
        @NotBlank String line1,
        String line2,
        @NotBlank String city,
        @NotBlank String state,
        @NotBlank String postalCode,
        @NotBlank String country,
        boolean defaultAddress) {
}
