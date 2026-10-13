package com.ecommerce.payment.gateway;

import com.ecommerce.payment.exception.ApiException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Razorpay, the default gateway when it is switched on (@Primary). Talks to Razorpay's REST API with the key id
 * and secret. The customer pays inside Razorpay's own checkout window, so card details never reach this server.
 * <p>
 * Switched OFF by default: the bean only exists when {@code razorpay.enabled=true} (env RAZORPAY_ENABLED=true).
 * While it is off, the mock is the only gateway and is used automatically.
 */
@Component
@Primary
@ConditionalOnProperty(name = "razorpay.enabled", havingValue = "true")
public class RazorpayPaymentGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(RazorpayPaymentGateway.class);

    private final RestClient client;
    private final String keyId;
    private final String keySecret;
    private final String webhookSecret;

    public RazorpayPaymentGateway(RestClient.Builder builder,
                                  @Value("${razorpay.base-url}") String baseUrl,
                                  @Value("${razorpay.key-id}") String keyId,
                                  @Value("${razorpay.key-secret}") String keySecret,
                                  @Value("${razorpay.webhook-secret}") String webhookSecret) {
        this.keyId = keyId.trim();
        this.keySecret = keySecret.trim();
        this.webhookSecret = webhookSecret.trim();
        RestClient.Builder configured = builder.baseUrl(baseUrl);
        if (isAvailable()) {
            configured.defaultHeaders(h -> h.setBasicAuth(this.keyId, this.keySecret));
        }
        this.client = configured.build();
    }

    @Override
    public boolean isAvailable() {
        return !keyId.isEmpty() && !keySecret.isEmpty();
    }

    /** The public key the browser needs to open Razorpay checkout. Safe to expose; the secret never leaves here. */
    public String keyId() {
        return keyId;
    }

    @Override
    public GatewayOrder createOrder(BigDecimal amount, String currency, String receipt) {
        requireConfigured();
        Map<String, Object> body = Map.of("amount", toPaise(amount), "currency", currency, "receipt", receipt);
        try {
            Map<?, ?> response = client.post().uri("/orders").contentType(MediaType.APPLICATION_JSON)
                    .body(body).retrieve().body(Map.class);
            Object id = response == null ? null : response.get("id");
            if (!(id instanceof String orderId) || orderId.isBlank()) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Razorpay returned an unexpected response");
            }
            return new GatewayOrder(orderId, keyId);
        } catch (RestClientResponseException e) {
            log.error("Razorpay order creation failed: HTTP {} {}", e.getStatusCode().value(),
                    e.getResponseBodyAsString());
            throw new ApiException(HttpStatus.BAD_GATEWAY, e.getStatusCode().value() == 401
                    ? "Razorpay rejected our API keys" : "Razorpay could not create the payment");
        } catch (ResourceAccessException e) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Razorpay is unavailable");
        }
    }

    /**
     * Verifies the proof the browser got from Razorpay checkout: signature = HMAC_SHA256(orderId + "|" + paymentId,
     * keySecret). Only Razorpay and we know the secret, so a valid signature means the payment really happened.
     */
    @Override
    public ChargeResult confirm(String gatewayOrderId, Map<String, String> details) {
        requireConfigured();
        String paymentId = details.get("razorpay_payment_id");
        String signature = details.get("razorpay_signature");
        if (paymentId == null || paymentId.isBlank() || signature == null || signature.isBlank()) {
            return new ChargeResult(false, null, null, null, "Payment details are missing");
        }
        String expected = Hmac.sha256Hex(keySecret, gatewayOrderId + "|" + paymentId.trim());
        if (!Hmac.matches(expected, signature)) {
            log.warn("Razorpay signature mismatch for gateway order {}", gatewayOrderId);
            return new ChargeResult(false, null, null, null, "Payment could not be verified");
        }
        return new ChargeResult(true, paymentId.trim(), "RAZORPAY", paymentId.trim(), null);
    }

    @Override
    public RefundResult refund(String gatewayPaymentId, BigDecimal amount) {
        requireConfigured();
        try {
            client.post().uri("/payments/{id}/refund", gatewayPaymentId).contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("amount", toPaise(amount))).retrieve().toBodilessEntity();
            return new RefundResult(true, null);
        } catch (RestClientResponseException e) {
            log.error("Razorpay refund of {} failed: HTTP {} {}", gatewayPaymentId, e.getStatusCode().value(),
                    e.getResponseBodyAsString());
            return new RefundResult(false, "Razorpay refused the refund");
        } catch (ResourceAccessException e) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Razorpay is unavailable");
        }
    }

    /** Checks the X-Razorpay-Signature header of a webhook: HMAC_SHA256(rawBody, webhookSecret). */
    public boolean verifyWebhook(String rawBody, String signature) {
        if (webhookSecret.isEmpty() || rawBody == null) {
            return false;
        }
        return Hmac.matches(Hmac.sha256Hex(webhookSecret, rawBody), signature);
    }

    private void requireConfigured() {
        if (!isAvailable()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Razorpay is not configured (set RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET)");
        }
    }

    /** Razorpay amounts are whole paise: 999.99 rupees is 99999. */
    private static long toPaise(BigDecimal amount) {
        return amount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }
}
