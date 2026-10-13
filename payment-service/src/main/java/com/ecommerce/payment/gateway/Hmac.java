package com.ecommerce.payment.gateway;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** HMAC-SHA256 helpers, which is how Razorpay signs payment confirmations and webhooks. */
final class Hmac {

    private Hmac() {
    }

    static String sha256Hex(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 is not available", e);
        }
    }

    /** Constant-time comparison, so a signature cannot be guessed one character at a time. */
    static boolean matches(String expected, String actual) {
        return actual != null
                && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                        actual.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));
    }
}
