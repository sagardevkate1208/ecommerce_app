package com.ecommerce.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateProfileRequest(@NotBlank String firstName, @NotBlank String lastName, String phone) {
}
