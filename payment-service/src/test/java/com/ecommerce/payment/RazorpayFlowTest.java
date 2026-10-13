package com.ecommerce.payment;

import static com.ecommerce.payment.TestTokens.admin;
import static com.ecommerce.payment.TestTokens.user;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecommerce.payment.client.OrderClient;
import com.ecommerce.payment.dto.OrderInfo;
import com.ecommerce.payment.exception.ApiException;
import com.ecommerce.payment.gateway.PaymentGateway;
import com.ecommerce.payment.gateway.RazorpayPaymentGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Razorpay switched ON (razorpay.enabled=true in the test profile): with no provider in the request, the @Primary
 * gateway is used. Razorpay itself is replaced by a mock here so no network is needed; the real class is tested in
 * RazorpayPaymentGatewayTest. Every test uses its own order id because the tests share one database.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RazorpayFlowTest {

    private static final AtomicInteger GATEWAY_ORDERS = new AtomicInteger();

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockitoBean OrderClient orderClient;
    @MockitoBean RazorpayPaymentGateway razorpay;

    @BeforeEach
    void stub() {
        when(razorpay.isAvailable()).thenReturn(true);
        when(razorpay.keyId()).thenReturn("rzp_test_abc");
        when(razorpay.createOrder(any(), eq("INR"), anyString())).thenAnswer(inv ->
                new PaymentGateway.GatewayOrder("order_RZP_" + GATEWAY_ORDERS.incrementAndGet(), "rzp_test_abc"));
    }

    private void pendingOrder(long orderId) {
        when(orderClient.getOrder(anyString(), eq(orderId)))
                .thenReturn(new OrderInfo(orderId, 20L, "PENDING", new BigDecimal("999.99")));
        when(orderClient.getOrderAsAdmin(anyString(), eq(orderId)))
                .thenReturn(new OrderInfo(orderId, 20L, "PAID", new BigDecimal("999.99")));
    }

    /** No provider in the request on purpose: the default must be Razorpay. */
    private long initiate(long orderId) throws Exception {
        String res = mvc.perform(post("/api/payments/initiate").header("Authorization", user(20))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"orderId\":" + orderId + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.provider").value("RAZORPAY"))
                .andExpect(jsonPath("$.gatewayOrderId", startsWith("order_RZP_")))
                .andExpect(jsonPath("$.keyId").value("rzp_test_abc"))
                .andExpect(jsonPath("$.amount").value(999.99))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(res).get("paymentId").asLong();
    }

    private void confirmApproved(long paymentId, String razorpayPaymentId) throws Exception {
        when(razorpay.confirm(anyString(), any())).thenReturn(
                new PaymentGateway.ChargeResult(true, razorpayPaymentId, "RAZORPAY", razorpayPaymentId, null));
        mvc.perform(post("/api/payments/" + paymentId + "/confirm").header("Authorization", user(20))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"details\":{\"razorpay_payment_id\":\"" + razorpayPaymentId
                                + "\",\"razorpay_signature\":\"sig\"}}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    @Test
    void configReportsRazorpayAsDefaultAndKeepsTheSecretOut() throws Exception {
        mvc.perform(get("/api/payments/config").header("Authorization", user(20)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultProvider").value("RAZORPAY"))
                .andExpect(jsonPath("$.razorpayConfigured").value(true))
                .andExpect(jsonPath("$.razorpayKeyId").value("rzp_test_abc"))
                .andExpect(jsonPath("$.mockEnabled").value(true))
                .andExpect(jsonPath("$.keySecret").doesNotExist());
    }

    @Test
    void defaultProviderIsRazorpayAndConfirmUsesItsVerdict() throws Exception {
        pendingOrder(200);
        long id = initiate(200);

        confirmApproved(id, "pay_1");

        mvc.perform(get("/api/payments/" + id).header("Authorization", user(20)))
                .andExpect(jsonPath("$.provider").value("RAZORPAY"))
                .andExpect(jsonPath("$.transactionRef").value("pay_1"))
                .andExpect(jsonPath("$.orderSynced").value(true));
        verify(orderClient).updateStatus(200L, "PAID");
    }

    @Test
    void aFailedVerificationNeverMarksTheOrderPaid() throws Exception {
        pendingOrder(201);
        long id = initiate(201);
        when(razorpay.confirm(anyString(), any()))
                .thenReturn(new PaymentGateway.ChargeResult(false, null, null, null, "Payment could not be verified"));

        mvc.perform(post("/api/payments/" + id + "/confirm").header("Authorization", user(20))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"details\":{\"razorpay_payment_id\":\"pay_x\",\"razorpay_signature\":\"forged\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureReason").value("Payment could not be verified"));
        verify(orderClient, never()).updateStatus(eq(201L), anyString());
    }

    @Test
    void razorpayWithoutKeysAnswersClearly() throws Exception {
        pendingOrder(202);
        when(razorpay.createOrder(any(), anyString(), anyString())).thenThrow(new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE, "Razorpay is not configured (set RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET)"));
        mvc.perform(post("/api/payments/initiate").header("Authorization", user(20))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"orderId\":202}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message", containsString("not configured")));
    }

    @Test
    void refundOfARazorpayPaymentGoesToRazorpayNotToTheMock() throws Exception {
        pendingOrder(203);
        long id = initiate(203);
        confirmApproved(id, "pay_9");
        when(razorpay.refund("pay_9", new BigDecimal("999.99")))
                .thenReturn(new PaymentGateway.RefundResult(true, null));

        mvc.perform(post("/api/admin/payments/" + id + "/refund").header("Authorization", admin()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REFUNDED"));
        verify(razorpay).refund("pay_9", new BigDecimal("999.99"));
        verify(orderClient).updateStatus(203L, "CANCELLED");
    }

    @Test
    void aRefundTheProviderRefusesChangesNothing() throws Exception {
        pendingOrder(204);
        long id = initiate(204);
        confirmApproved(id, "pay_8");
        when(razorpay.refund(anyString(), any()))
                .thenReturn(new PaymentGateway.RefundResult(false, "Razorpay refused the refund"));

        mvc.perform(post("/api/admin/payments/" + id + "/refund").header("Authorization", admin()))
                .andExpect(status().isBadGateway());
        mvc.perform(get("/api/payments/" + id).header("Authorization", user(20)))
                .andExpect(jsonPath("$.status").value("SUCCESS"));
        verify(orderClient, never()).updateStatus(204L, "CANCELLED");
    }

    @Test
    void theMockIsStillReachableByItsQualifierWhileRazorpayIsEnabled() throws Exception {
        pendingOrder(205);
        mvc.perform(post("/api/payments/initiate").header("Authorization", user(20))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"orderId\":205,\"provider\":\"MOCK\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.provider").value("MOCK"));
        verify(razorpay, never()).createOrder(any(), anyString(), eq("order_205"));
    }
}
