package com.ecommerce.payment.gateway;

import com.ecommerce.payment.exception.ApiException;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Fake provider for development and tests. Nothing is charged. Select it with the qualifier "mock":
 * {@code @Qualifier("mock") PaymentGateway}.
 * <p>
 * Never active in production: there only Razorpay exists, so payments can never be approved without being charged.
 * <p>
 * Decline rules, so both outcomes can be tested: a card number ending in 0002, or a UPI id starting with "fail".
 */
@Component
@Qualifier("mock")
@Profile({"dev", "test"})
public class MockPaymentGateway implements PaymentGateway {

    @Override
    public GatewayOrder createOrder(BigDecimal amount, String currency, String receipt) {
        return new GatewayOrder("mock_order_" + UUID.randomUUID(), null);
    }

    /** details: "method" (CARD or UPI) and "reference" (the card number or the UPI id). */
    @Override
    public ChargeResult confirm(String gatewayOrderId, Map<String, String> details) {
        String method = String.valueOf(details.get("method")).trim().toUpperCase();
        String reference = details.get("reference");
        if (!method.equals("CARD") && !method.equals("UPI")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "method must be CARD or UPI");
        }
        if (reference == null || reference.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "reference is required");
        }
        String value = normalise(method, reference);
        String masked = mask(method, value);

        boolean declined = method.equals("CARD") ? value.endsWith("0002") : value.toLowerCase().startsWith("fail");
        if (declined) {
            return new ChargeResult(false, null, method, masked, "Payment declined by the provider");
        }
        return new ChargeResult(true, "mock_pay_" + UUID.randomUUID(), method, masked, null);
    }

    @Override
    public RefundResult refund(String gatewayPaymentId, BigDecimal amount) {
        return new RefundResult(true, null);
    }

    private String normalise(String method, String reference) {
        String value = reference.trim();
        if (method.equals("CARD")) {
            value = value.replaceAll("[\\s-]", "");
            if (!value.matches("\\d{13,19}")) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Card number must be 13 to 19 digits");
            }
        } else if (!value.matches("[^@\\s]+@[^@\\s]+")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "UPI id must look like name@bank");
        }
        return value;
    }

    private String mask(String method, String value) {
        if (method.equals("CARD")) {
            return "**** **** **** " + value.substring(value.length() - 4);
        }
        return value.charAt(0) + "***" + value.substring(value.indexOf('@'));
    }
}
