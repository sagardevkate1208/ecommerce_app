package com.ecommerce.payment.dto;

import java.math.BigDecimal;

/** Everything the browser needs to open the provider's checkout. keyId is public; it is null for the mock. */
public record InitiateResponse(Long paymentId, String provider, String gatewayOrderId, BigDecimal amount,
                               String currency, String keyId) {
}
