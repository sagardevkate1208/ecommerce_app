package com.ecommerce.auth.dto;

import com.ecommerce.auth.entity.Role;
public record ProfileResponse(Long id, String email, String firstName, String lastName, String phone, String role) {
}
