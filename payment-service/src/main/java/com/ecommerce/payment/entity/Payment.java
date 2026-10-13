package com.ecommerce.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One payment attempt for an order. Card numbers and UPI ids are never stored in full, only a masked form.
 * Status and method are plain text columns (not MySQL ENUMs) so new values never need a table change.
 */
@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long orderId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    /** RAZORPAY or MOCK. Null on rows created before gateways existed; those are treated as MOCK. */
    private String provider;

    /** The gateway's own id for this payment session (Razorpay: order_xxx). */
    @Column(unique = true)
    private String gatewayOrderId;

    /** CARD / UPI for the mock; RAZORPAY until Razorpay tells us more. */
    @Column(nullable = false)
    private String method;

    /** Masked card / UPI id, or the gateway payment id. */
    private String maskedReference;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    /** The gateway's id for the actual payment (Razorpay: pay_xxx). Needed to refund. */
    private String transactionRef;

    private String failureReason;

    /** False when the money was taken but the order service could not be told (needs a retry). */
    @Column(nullable = false)
    private boolean orderSynced;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    public Long getId() { return id; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getGatewayOrderId() { return gatewayOrderId; }
    public void setGatewayOrderId(String gatewayOrderId) { this.gatewayOrderId = gatewayOrderId; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public String getMaskedReference() { return maskedReference; }
    public void setMaskedReference(String maskedReference) { this.maskedReference = maskedReference; }
    public PaymentStatus getStatus() { return status; }
    public void setStatus(PaymentStatus status) { this.status = status; }
    public String getTransactionRef() { return transactionRef; }
    public void setTransactionRef(String transactionRef) { this.transactionRef = transactionRef; }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    public boolean isOrderSynced() { return orderSynced; }
    public void setOrderSynced(boolean orderSynced) { this.orderSynced = orderSynced; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void touch() { this.updatedAt = Instant.now(); }
}
