package com.ecommerce.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecommerce.payment.client.OrderClient;
import com.ecommerce.payment.entity.Payment;
import com.ecommerce.payment.entity.PaymentStatus;
import com.ecommerce.payment.repository.PaymentRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Razorpay's webhook: open to the internet, so everything depends on the signature. Real gateway bean, fake secret. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RazorpayWebhookTest {

    private static final String WEBHOOK_SECRET = "test_webhook_secret"; // from application-test.properties

    @Autowired MockMvc mvc;
    @Autowired PaymentRepository payments;
    @MockitoBean OrderClient orderClient;

    private static String sign(String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }

    private Payment createdPayment(long orderId, String gatewayOrderId) {
        Payment p = new Payment();
        p.setOrderId(orderId);
        p.setUserId(1L);
        p.setAmount(new BigDecimal("100.00"));
        p.setProvider("RAZORPAY");
        p.setGatewayOrderId(gatewayOrderId);
        p.setMethod("RAZORPAY");
        p.setStatus(PaymentStatus.CREATED);
        return payments.save(p);
    }

    private static String event(String event, String gatewayOrderId, String paymentId) {
        return "{\"event\":\"" + event + "\",\"payload\":{\"payment\":{\"entity\":{\"id\":\"" + paymentId
                + "\",\"order_id\":\"" + gatewayOrderId + "\",\"method\":\"upi\",\"status\":\"captured\","
                + "\"error_description\":\"Bank declined\"}}}}";
    }

    private void send(String body, String signature, int expectedStatus) throws Exception {
        var request = post("/api/payments/webhook").contentType(MediaType.APPLICATION_JSON).content(body);
        if (signature != null) {
            request = request.header("X-Razorpay-Signature", signature);
        }
        mvc.perform(request).andExpect(status().is(expectedStatus));
    }

    @Test
    void aSignedCaptureMarksThePaymentAndOrderPaidExactlyOnce() throws Exception {
        Payment p = createdPayment(300, "order_WH1");
        String body = event("payment.captured", "order_WH1", "pay_WH1");

        send(body, sign(body), 200);

        Payment after = payments.findById(p.getId()).orElseThrow();
        assertEquals(PaymentStatus.SUCCESS, after.getStatus());
        assertEquals("pay_WH1", after.getTransactionRef());
        assertEquals("UPI", after.getMethod());
        assertEquals(true, after.isOrderSynced());
        verify(orderClient, times(1)).updateStatus(300L, "PAID");

        // Razorpay retries and the browser may confirm too: replaying changes nothing
        send(body, sign(body), 200);
        verify(orderClient, times(1)).updateStatus(300L, "PAID");
    }

    @Test
    void unsignedOrWronglySignedCallsAreRejectedAndChangeNothing() throws Exception {
        Payment p = createdPayment(301, "order_WH2");
        String body = event("payment.captured", "order_WH2", "pay_WH2");

        send(body, null, 400);
        send(body, "deadbeef", 400);
        send(body, sign(body + " tampered"), 400);

        assertEquals(PaymentStatus.CREATED, payments.findById(p.getId()).orElseThrow().getStatus());
        verify(orderClient, never()).updateStatus(anyLong(), anyString());
    }

    @Test
    void aFailureEventMarksFailedButNeverUndoesASuccess() throws Exception {
        Payment failing = createdPayment(302, "order_WH3");
        String failed = event("payment.failed", "order_WH3", "pay_WH3");
        send(failed, sign(failed), 200);
        Payment afterFail = payments.findById(failing.getId()).orElseThrow();
        assertEquals(PaymentStatus.FAILED, afterFail.getStatus());
        assertEquals("Bank declined", afterFail.getFailureReason());

        Payment paid = createdPayment(303, "order_WH4");
        String captured = event("payment.captured", "order_WH4", "pay_WH4");
        send(captured, sign(captured), 200);
        String lateFail = event("payment.failed", "order_WH4", "pay_WH4b");
        send(lateFail, sign(lateFail), 200);
        assertEquals(PaymentStatus.SUCCESS, payments.findById(paid.getId()).orElseThrow().getStatus());
    }

    @Test
    void unknownOrdersAndUninterestingEventsAreAcknowledgedAndIgnored() throws Exception {
        String unknown = event("payment.captured", "order_DOES_NOT_EXIST", "pay_X");
        send(unknown, sign(unknown), 200);

        String other = event("refund.created", "order_WH1", "pay_X");
        send(other, sign(other), 200);
        verify(orderClient, never()).updateStatus(anyLong(), anyString());
    }
}
