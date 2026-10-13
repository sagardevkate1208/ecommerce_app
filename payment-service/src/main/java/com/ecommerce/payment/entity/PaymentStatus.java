package com.ecommerce.payment.entity;

public enum PaymentStatus {
    /** The gateway order exists; the customer has not paid yet (or has not been confirmed yet). */
    CREATED,
    SUCCESS,
    FAILED,
    REFUNDED
}
