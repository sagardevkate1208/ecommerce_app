package com.ecommerce.payment.dto;

import com.ecommerce.payment.entity.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponse(
        Long id, Long orderId, Long userId, BigDecimal amount, String provider, String gatewayOrderId, String method,
        String maskedReference, PaymentStatus status, String transactionRef, String failureReason,
        boolean orderSynced, Instant createdAt, Instant updatedAt) {
}
