package com.ecommerce.payment.gateway;

import java.math.BigDecimal;
import java.util.Map;

/**
 * A payment provider. Every provider works in the same three steps, which is how real gateways work:
 * <ol>
 *   <li>{@link #createOrder}: our server registers the amount with the provider.</li>
 *   <li>The customer pays directly with the provider (card details never reach our server).</li>
 *   <li>{@link #confirm}: our server checks the provider's proof that the payment happened.</li>
 * </ol>
 * Implementations: {@link RazorpayPaymentGateway} (the default, marked @Primary) and
 * {@link MockPaymentGateway} (qualifier "mock", dev and test only).
 */
public interface PaymentGateway {

    /** False when the provider is not usable yet, for example missing API keys. */
    default boolean isAvailable() {
        return true;
    }

    GatewayOrder createOrder(BigDecimal amount, String currency, String receipt);

    /** details are provider specific: Razorpay sends razorpay_payment_id and razorpay_signature. */
    ChargeResult confirm(String gatewayOrderId, Map<String, String> details);

    RefundResult refund(String gatewayPaymentId, BigDecimal amount);

    /** keyId is the provider's public key the browser needs to open checkout; null when there is none. */
    record GatewayOrder(String gatewayOrderId, String keyId) {
    }

    record ChargeResult(boolean approved, String gatewayPaymentId, String method, String maskedReference,
                        String failureReason) {
    }

    record RefundResult(boolean approved, String failureReason) {
    }
}
