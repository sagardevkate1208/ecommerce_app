package com.ecommerce.payment.dto;

import java.math.BigDecimal;

/** The subset of Order's response that payment needs. */
public record OrderInfo(Long id, Long userId, String status, BigDecimal totalAmount) {
}
