package com.ecommerce.payment.controller;

import com.ecommerce.payment.gateway.RazorpayPaymentGateway;
import com.ecommerce.payment.service.PaymentService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Razorpay calls this URL itself when a payment is captured or fails. It exists because the customer may close the
 * browser after paying and before our confirm call is made; without it that payment would never be recorded.
 * <p>
 * It is open to the internet (no user token), so every call is authenticated by Razorpay's signature instead.
 */
@RestController
@RequestMapping("/api/payments/webhook")
@ConditionalOnProperty(name = "razorpay.enabled", havingValue = "true")
public class RazorpayWebhookController {

    private static final Logger log = LoggerFactory.getLogger(RazorpayWebhookController.class);

    private final RazorpayPaymentGateway razorpay;
    private final PaymentService paymentService;
    private final ObjectMapper mapper;

    public RazorpayWebhookController(RazorpayPaymentGateway razorpay, PaymentService paymentService,
                                     ObjectMapper mapper) {
        this.razorpay = razorpay;
        this.paymentService = paymentService;
        this.mapper = mapper;
    }

    /** The body must be read as the raw text: the signature is computed over the exact bytes Razorpay sent. */
    @PostMapping
    public ResponseEntity<Void> receive(@RequestBody String body,
                                        @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {
        if (!razorpay.verifyWebhook(body, signature)) {
            log.warn("Rejected a Razorpay webhook with a missing or invalid signature");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        try {
            JsonNode root = mapper.readTree(body);
            String event = root.path("event").asText();
            JsonNode payment = root.path("payload").path("payment").path("entity");
            String gatewayOrderId = payment.path("order_id").asText(null);

            if (gatewayOrderId != null) {
                switch (event) {
                    case "payment.captured", "order.paid" -> paymentService.recordGatewayCapture(
                            gatewayOrderId, payment.path("id").asText(), payment.path("method").asText(null));
                    case "payment.failed" -> paymentService.recordGatewayFailure(
                            gatewayOrderId, payment.path("error_description").asText("Payment failed"));
                    default -> log.debug("Ignoring Razorpay event {}", event);
                }
            }
        } catch (Exception e) {
            // A 5xx makes Razorpay retry later, which is what we want for a temporary problem.
            log.error("Could not process a Razorpay webhook", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        return ResponseEntity.ok().build();
    }
}
