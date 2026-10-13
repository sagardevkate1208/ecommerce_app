package com.ecommerce.payment;

import static com.ecommerce.payment.TestTokens.admin;
import static com.ecommerce.payment.TestTokens.user;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecommerce.payment.client.OrderClient;
import com.ecommerce.payment.dto.OrderInfo;
import com.ecommerce.payment.exception.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** The whole payment flow through the MOCK gateway (reached with the "mock" qualifier). Razorpay is covered elsewhere. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentFlowTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockitoBean OrderClient orderClient;

    private static final String GOOD_CARD = "4111 1111 1111 1111";
    private static final String DECLINED_CARD = "4000000000000002";

    private void orderIs(long orderId, long userId, String status, String total) {
        OrderInfo info = new OrderInfo(orderId, userId, status, new BigDecimal(total));
        when(orderClient.getOrder(anyString(), eq(orderId))).thenReturn(info);
        when(orderClient.getOrderAsAdmin(anyString(), eq(orderId))).thenReturn(info);
    }

    private String initiateBody(long orderId, String provider) {
        return "{\"orderId\":" + orderId + (provider == null ? "" : ",\"provider\":\"" + provider + "\"") + "}";
    }

    private String cardDetails(String number) {
        return "{\"details\":{\"method\":\"CARD\",\"reference\":\"" + number + "\"}}";
    }

    private long initiateMock(long userId, long orderId) throws Exception {
        String res = mvc.perform(post("/api/payments/initiate").header("Authorization", user(userId))
                        .contentType(MediaType.APPLICATION_JSON).content(initiateBody(orderId, "MOCK")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.provider").value("MOCK"))
                .andExpect(jsonPath("$.gatewayOrderId", startsWith("mock_order_")))
                .andExpect(jsonPath("$.currency").value("INR"))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(res).get("paymentId").asLong();
    }

    private void confirm(long userId, long paymentId, String body, String expectedStatus) throws Exception {
        mvc.perform(post("/api/payments/" + paymentId + "/confirm").header("Authorization", user(userId))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(expectedStatus));
    }

    @Test
    void requiresLogin() throws Exception {
        mvc.perform(get("/api/payments")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/payments/initiate").contentType(MediaType.APPLICATION_JSON).content(initiateBody(1, null)))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/payments/config")).andExpect(status().isUnauthorized());
    }

    @Test
    void configShowsWhichOptionsExist() throws Exception {
        mvc.perform(get("/api/payments/config").header("Authorization", user(5)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultProvider").value("RAZORPAY"))
                .andExpect(jsonPath("$.razorpayConfigured").value(true))
                .andExpect(jsonPath("$.razorpayKeyId").value("rzp_test_key"))
                .andExpect(jsonPath("$.mockEnabled").value(true));
    }

    @Test
    void mockPaymentMarksOrderPaidOnceAndMasksTheCard() throws Exception {
        orderIs(100, 10, "PENDING", "2598.98");
        long id = initiateMock(10, 100);

        mvc.perform(post("/api/payments/" + id + "/confirm").header("Authorization", user(10))
                        .contentType(MediaType.APPLICATION_JSON).content(cardDetails(GOOD_CARD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.amount").value(2598.98))
                .andExpect(jsonPath("$.method").value("CARD"))
                .andExpect(jsonPath("$.maskedReference").value("**** **** **** 1111"))
                .andExpect(jsonPath("$.orderSynced").value(true));
        verify(orderClient, times(1)).updateStatus(100L, "PAID");

        // confirming again changes nothing and does not tell Order a second time
        confirm(10, id, cardDetails(GOOD_CARD), "SUCCESS");
        verify(orderClient, times(1)).updateStatus(100L, "PAID");

        // and the order cannot be paid twice
        mvc.perform(post("/api/payments/initiate").header("Authorization", user(10))
                        .contentType(MediaType.APPLICATION_JSON).content(initiateBody(100, "MOCK")))
                .andExpect(status().isConflict());
    }

    @Test
    void declinedPaymentCanBeRetriedOnTheSamePayment() throws Exception {
        orderIs(101, 11, "PENDING", "75.00");
        long id = initiateMock(11, 101);

        mvc.perform(post("/api/payments/" + id + "/confirm").header("Authorization", user(11))
                        .contentType(MediaType.APPLICATION_JSON).content(cardDetails(DECLINED_CARD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureReason").value("Payment declined by the provider"));
        verify(orderClient, never()).updateStatus(eq(101L), anyString());

        confirm(11, id, "{\"details\":{\"method\":\"UPI\",\"reference\":\"jane@okbank\"}}", "SUCCESS");
        verify(orderClient).updateStatus(101L, "PAID");
    }

    @Test
    void rejectsUnpayableOrdersBadProvidersAndBadInput() throws Exception {
        orderIs(110, 12, "SHIPPED", "10.00");
        mvc.perform(post("/api/payments/initiate").header("Authorization", user(12))
                        .contentType(MediaType.APPLICATION_JSON).content(initiateBody(110, "MOCK")))
                .andExpect(status().isConflict());

        // someone else's order: Order answers 404 for it
        when(orderClient.getOrder(anyString(), eq(111L)))
                .thenThrow(new ApiException(HttpStatus.NOT_FOUND, "Order not found"));
        mvc.perform(post("/api/payments/initiate").header("Authorization", user(12))
                        .contentType(MediaType.APPLICATION_JSON).content(initiateBody(111, "MOCK")))
                .andExpect(status().isNotFound());

        orderIs(112, 12, "PENDING", "10.00");
        mvc.perform(post("/api/payments/initiate").header("Authorization", user(12))
                        .contentType(MediaType.APPLICATION_JSON).content(initiateBody(112, "PAYPAL")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/payments/initiate").header("Authorization", user(12))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());

        long id = initiateMock(12, 112);
        mvc.perform(post("/api/payments/" + id + "/confirm").header("Authorization", user(12))
                        .contentType(MediaType.APPLICATION_JSON).content(cardDetails("1234")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/payments/" + id + "/confirm").header("Authorization", user(12))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"details\":{}}"))
                .andExpect(status().isBadRequest());
        // another customer cannot confirm this payment
        mvc.perform(post("/api/payments/" + id + "/confirm").header("Authorization", user(99))
                        .contentType(MediaType.APPLICATION_JSON).content(cardDetails(GOOD_CARD)))
                .andExpect(status().isNotFound());
    }

    @Test
    void customersSeeOnlyTheirOwnPayments() throws Exception {
        orderIs(120, 13, "PENDING", "20.00");
        long id = initiateMock(13, 120);
        mvc.perform(get("/api/payments").header("Authorization", user(13)))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].status").value("CREATED"));
        mvc.perform(get("/api/payments/" + id).header("Authorization", user(13))).andExpect(status().isOk());
        mvc.perform(get("/api/payments/" + id).header("Authorization", user(14))).andExpect(status().isNotFound());
        mvc.perform(get("/api/payments").header("Authorization", user(14))).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void orderSyncFailureKeepsThePaymentAndAdminCanRetry() throws Exception {
        orderIs(130, 15, "PENDING", "30.00");
        doThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Order service is unavailable"))
                .when(orderClient).updateStatus(130L, "PAID");
        long id = initiateMock(15, 130);
        confirm(15, id, cardDetails(GOOD_CARD), "SUCCESS");
        mvc.perform(get("/api/payments/" + id).header("Authorization", user(15)))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.orderSynced").value(false));

        doNothing().when(orderClient).updateStatus(130L, "PAID");
        mvc.perform(post("/api/admin/payments/" + id + "/sync-order").header("Authorization", user(15)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/payments/" + id + "/sync-order").header("Authorization", admin()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.orderSynced").value(true));
    }

    @Test
    void adminRefundReturnsMoneyThenCancelsOrderOnce() throws Exception {
        orderIs(140, 16, "PENDING", "40.00");
        long id = initiateMock(16, 140);
        confirm(16, id, cardDetails(GOOD_CARD), "SUCCESS");
        orderIs(140, 16, "PAID", "40.00"); // what Order reports once payment has been recorded

        mvc.perform(post("/api/admin/payments/" + id + "/refund").header("Authorization", user(16)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/payments/" + id + "/refund").header("Authorization", admin()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REFUNDED"));
        verify(orderClient).updateStatus(140L, "CANCELLED");

        mvc.perform(post("/api/admin/payments/" + id + "/refund").header("Authorization", admin()))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/admin/payments").header("Authorization", admin()).param("orderId", "140"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].orderId").value(140));
    }

    @Test
    void refundIsBlockedBeforeAnyMoneyMovesWhenOrderAlreadyShipped() throws Exception {
        orderIs(150, 17, "PENDING", "15.00");
        long id = initiateMock(17, 150);
        confirm(17, id, cardDetails(GOOD_CARD), "SUCCESS");
        orderIs(150, 17, "SHIPPED", "15.00");

        mvc.perform(post("/api/admin/payments/" + id + "/refund").header("Authorization", admin()))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/payments/" + id).header("Authorization", user(17)))
                .andExpect(jsonPath("$.status").value("SUCCESS"));
        verify(orderClient, never()).updateStatus(150L, "CANCELLED");
    }

    @Test
    void refundStillSucceedsIfTheOrderCannotBeCancelledAfterwardsButIsFlagged() throws Exception {
        orderIs(160, 18, "PENDING", "25.00");
        long id = initiateMock(18, 160);
        confirm(18, id, cardDetails(GOOD_CARD), "SUCCESS");
        orderIs(160, 18, "PAID", "25.00");
        doThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Order service is unavailable"))
                .when(orderClient).updateStatus(160L, "CANCELLED");

        mvc.perform(post("/api/admin/payments/" + id + "/refund").header("Authorization", admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"))
                .andExpect(jsonPath("$.orderSynced").value(false));
    }
}
