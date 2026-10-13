package com.ecommerce.payment;

import static com.ecommerce.payment.TestTokens.user;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecommerce.payment.client.OrderClient;
import com.ecommerce.payment.dto.OrderInfo;
import com.ecommerce.payment.gateway.RazorpayPaymentGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** The real everyday setup: razorpay.enabled=false, so the mock is the only gateway and needs no keys. */
@SpringBootTest(properties = "razorpay.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MockOnlyPaymentTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ApplicationContext context;
    @MockitoBean OrderClient orderClient;

    @Test
    void razorpayBeansDoNotExistWhileItIsSwitchedOff() {
        org.junit.jupiter.api.Assertions.assertEquals(0, context.getBeansOfType(RazorpayPaymentGateway.class).size());
    }

    @Test
    void configSaysMockIsTheDefaultAndRazorpayIsOff() throws Exception {
        mvc.perform(get("/api/payments/config").header("Authorization", user(30)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultProvider").value("MOCK"))
                .andExpect(jsonPath("$.razorpayConfigured").value(false))
                .andExpect(jsonPath("$.razorpayKeyId").doesNotExist())
                .andExpect(jsonPath("$.mockEnabled").value(true));
    }

    @Test
    void paymentWorksEndToEndWithNoProviderInTheRequest() throws Exception {
        when(orderClient.getOrder(anyString(), eq(400L)))
                .thenReturn(new OrderInfo(400L, 30L, "PENDING", new BigDecimal("120.00")));

        String res = mvc.perform(post("/api/payments/initiate").header("Authorization", user(30))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"orderId\":400}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.provider").value("MOCK"))
                .andExpect(jsonPath("$.gatewayOrderId", startsWith("mock_order_")))
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(res).get("paymentId").asLong();

        mvc.perform(post("/api/payments/" + id + "/confirm").header("Authorization", user(30))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"details\":{\"method\":\"UPI\",\"reference\":\"jane@okbank\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.maskedReference").value("j***@okbank"));
        verify(orderClient).updateStatus(400L, "PAID");
    }

    @Test
    void askingForRazorpayWhileItIsOffIsRefusedInsteadOfQuietlyUsingTheMock() throws Exception {
        when(orderClient.getOrder(anyString(), eq(401L)))
                .thenReturn(new OrderInfo(401L, 30L, "PENDING", new BigDecimal("10.00")));
        mvc.perform(post("/api/payments/initiate").header("Authorization", user(30))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"orderId\":401,\"provider\":\"RAZORPAY\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("not enabled")));
    }

    @Test
    void theWebhookIsNotProcessedWhileRazorpayIsOff() throws Exception {
        // There is no webhook controller. The path falls into GET /api/payments/{id}, so POST is refused (405).
        mvc.perform(post("/api/payments/webhook").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().is4xxClientError());
    }
}
