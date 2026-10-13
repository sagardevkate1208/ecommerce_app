package com.ecommerce.payment.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.Map;

/**
 * Provider specific proof of payment. Razorpay: razorpay_payment_id and razorpay_signature.
 * Mock: method (CARD or UPI) and reference (card number or UPI id).
 */
public record ConfirmRequest(@NotEmpty Map<String, String> details) {
}
