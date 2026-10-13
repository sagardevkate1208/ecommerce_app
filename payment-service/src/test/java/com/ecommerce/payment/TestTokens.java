package com.ecommerce.payment;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/** Builds the same kind of signed JWT that auth-service issues, using the test secret. */
final class TestTokens {

    private static final String SECRET = "test-secret-test-secret-test-secret-1234";

    private TestTokens() {
    }

    static String bearer(long userId, String role) {
        return "Bearer " + Jwts.builder().subject(String.valueOf(userId)).claim("role", role)
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
    }

    static String user(long id) {
        return bearer(id, "CUSTOMER");
    }

    static String admin() {
        return bearer(1, "ADMIN");
    }
}
