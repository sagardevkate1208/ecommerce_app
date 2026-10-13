package com.ecommerce.catalog.dto;

import jakarta.validation.constraints.NotBlank;

public record CategoryRequest(@NotBlank String name, String description) {
}
