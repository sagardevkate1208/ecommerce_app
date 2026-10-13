package com.ecommerce.auth.dto;

import com.ecommerce.auth.entity.Role;
public record AuthResponse(String token, String tokenType, long expiresInMs, Long userId, String email, String role) {
}
