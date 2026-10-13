package com.ecommerce.payment.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.ecommerce.payment.exception.ApiException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** The real Razorpay class, with its HTTP calls answered by a fake server. No network, no Spring context. */
class RazorpayPaymentGatewayTest {

    private static final String BASE = "https://razorpay.test/v1";
    private static final String KEY_ID = "rzp_test_key";
    private static final String SECRET = "key_secret";

    private MockRestServiceServer server;
    private RazorpayPaymentGateway gateway;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        gateway = new RazorpayPaymentGateway(builder, BASE, KEY_ID, SECRET, "webhook_secret");
    }

    @Test
    void hmacImplementationMatchesTheRfc4231TestVector() {
        // RFC 4231, test case 2. If this passes, our signatures are standard HMAC-SHA256.
        assertEquals("5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843",
                Hmac.sha256Hex("Jefe", "what do ya want for nothing?"));
    }

    @Test
    void createOrderSendsAmountInPaiseWithBasicAuthAndReturnsTheOrderId() {
        String basic = Base64.getEncoder().encodeToString((KEY_ID + ":" + SECRET).getBytes(StandardCharsets.UTF_8));
        server.expect(requestTo(BASE + "/orders"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Basic " + basic))
                .andExpect(jsonPath("$.amount").value(99999))
                .andExpect(jsonPath("$.currency").value("INR"))
                .andExpect(jsonPath("$.receipt").value("order_7"))
                .andRespond(withSuccess("{\"id\":\"order_ABC\",\"status\":\"created\"}", MediaType.APPLICATION_JSON));

        PaymentGateway.GatewayOrder order = gateway.createOrder(new BigDecimal("999.99"), "INR", "order_7");

        assertEquals("order_ABC", order.gatewayOrderId());
        assertEquals(KEY_ID, order.keyId());
        server.verify();
    }

    @Test
    void createOrderTurnsRazorpayErrorsIntoClearApiErrors() {
        server.expect(requestTo(BASE + "/orders")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        ApiException badKeys = assertThrows(ApiException.class,
                () -> gateway.createOrder(new BigDecimal("10"), "INR", "order_1"));
        assertEquals(HttpStatus.BAD_GATEWAY, badKeys.getStatus());
        assertTrue(badKeys.getMessage().contains("API keys"));

        server.reset();
        server.expect(requestTo(BASE + "/orders")).andRespond(withServerError());
        ApiException down = assertThrows(ApiException.class,
                () -> gateway.createOrder(new BigDecimal("10"), "INR", "order_1"));
        assertEquals(HttpStatus.BAD_GATEWAY, down.getStatus());
    }

    @Test
    void withoutKeysItIsUnavailableAndSaysHowToFixIt() {
        RazorpayPaymentGateway unconfigured =
                new RazorpayPaymentGateway(RestClient.builder(), BASE, "", "", "");
        assertFalse(unconfigured.isAvailable());
        ApiException e = assertThrows(ApiException.class,
                () -> unconfigured.createOrder(new BigDecimal("10"), "INR", "order_1"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getStatus());
        assertTrue(e.getMessage().contains("RAZORPAY_KEY_ID"));
    }

    @Test
    void confirmAcceptsOnlyTheGenuineSignature() {
        String good = Hmac.sha256Hex(SECRET, "order_ABC|pay_123");

        assertTrue(gateway.confirm("order_ABC",
                Map.of("razorpay_payment_id", "pay_123", "razorpay_signature", good)).approved());
        // case of the hex does not matter
        assertTrue(gateway.confirm("order_ABC",
                Map.of("razorpay_payment_id", "pay_123", "razorpay_signature", good.toUpperCase())).approved());

        // a forged signature, a signature for a different payment, and a different order are all refused
        assertFalse(gateway.confirm("order_ABC",
                Map.of("razorpay_payment_id", "pay_123", "razorpay_signature", "deadbeef")).approved());
        assertFalse(gateway.confirm("order_ABC",
                Map.of("razorpay_payment_id", "pay_999", "razorpay_signature", good)).approved());
        assertFalse(gateway.confirm("order_OTHER",
                Map.of("razorpay_payment_id", "pay_123", "razorpay_signature", good)).approved());
        // missing details
        assertFalse(gateway.confirm("order_ABC", Map.of("razorpay_payment_id", "pay_123")).approved());
        assertFalse(gateway.confirm("order_ABC", Map.of()).approved());
    }

    @Test
    void confirmReturnsThePaymentIdForRefundsLater() {
        PaymentGateway.ChargeResult result = gateway.confirm("order_ABC", Map.of(
                "razorpay_payment_id", "pay_123", "razorpay_signature", Hmac.sha256Hex(SECRET, "order_ABC|pay_123")));
        assertEquals("pay_123", result.gatewayPaymentId());
        assertEquals("RAZORPAY", result.method());
    }

    @Test
    void refundCallsTheRefundEndpointWithPaiseAndReportsTheOutcome() {
        server.expect(requestTo(BASE + "/payments/pay_123/refund"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.amount").value(5000))
                .andRespond(withSuccess("{\"id\":\"rfnd_1\"}", MediaType.APPLICATION_JSON));
        assertTrue(gateway.refund("pay_123", new BigDecimal("50.00")).approved());
        server.verify();

        server.reset();
        server.expect(requestTo(BASE + "/payments/pay_123/refund")).andRespond(withBadRequest());
        PaymentGateway.RefundResult refused = gateway.refund("pay_123", new BigDecimal("50.00"));
        assertFalse(refused.approved());
        assertEquals("Razorpay refused the refund", refused.failureReason());
    }

    @Test
    void webhookSignatureIsCheckedAgainstTheWebhookSecretNotTheKeySecret() {
        String body = "{\"event\":\"payment.captured\"}";
        assertTrue(gateway.verifyWebhook(body, Hmac.sha256Hex("webhook_secret", body)));
        assertFalse(gateway.verifyWebhook(body, Hmac.sha256Hex(SECRET, body)));
        assertFalse(gateway.verifyWebhook(body + " ", Hmac.sha256Hex("webhook_secret", body)));
        assertFalse(gateway.verifyWebhook(body, null));

        // with no webhook secret configured, every webhook is refused
        RazorpayPaymentGateway noSecret = new RazorpayPaymentGateway(RestClient.builder(), BASE, KEY_ID, SECRET, "");
        assertFalse(noSecret.verifyWebhook(body, Hmac.sha256Hex("webhook_secret", body)));
        assertFalse(noSecret.verifyWebhook(body, ""));
    }
}
