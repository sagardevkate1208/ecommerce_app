package com.ecommerce.payment.dto;

/** Tells the UI which payment options exist, so it can show or hide them. Nothing secret is included. */
public record PaymentConfigResponse(String defaultProvider, boolean razorpayConfigured, String razorpayKeyId,
                                    boolean mockEnabled) {
}
