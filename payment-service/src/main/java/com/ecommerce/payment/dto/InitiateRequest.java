package com.ecommerce.payment.dto;

import jakarta.validation.constraints.NotNull;

/** provider is optional: RAZORPAY (the default) or MOCK (dev and test only). */
public record InitiateRequest(@NotNull Long orderId, String provider) {
}
