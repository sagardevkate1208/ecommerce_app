package com.ecommerce.order;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecommerce.order.client.CartClient;
import com.ecommerce.order.client.CatalogClient;
import com.ecommerce.order.dto.CartInfo;
import com.ecommerce.order.dto.ProductInfo;
import com.ecommerce.order.exception.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderFlowTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Value("${app.jwt.secret}") String secret;
    @MockitoBean CartClient cartClient;
    @MockitoBean CatalogClient catalogClient;

    private static final String CHECKOUT = """
            {"shippingAddress":{"recipientName":"Jane Doe","phone":"9876543210","line1":"1 Main St",
             "city":"Pune","state":"MH","postalCode":"411001","country":"IN"}}""";

    @BeforeEach
    void stub() {
        Map<Long, ProductInfo> products = Map.of(
                1L, new ProductInfo(1L, "UltraBook 14", "UB-14", new BigDecimal("999.99"), true),
                2L, new ProductInfo(2L, "Pixel Phone", "PH-1", new BigDecimal("599.00"), true),
                3L, new ProductInfo(3L, "Retired", "OLD-1", new BigDecimal("10.00"), false));
        when(catalogClient.getProduct(anyLong())).thenAnswer(inv -> {
            ProductInfo p = products.get(inv.<Long>getArgument(0));
            if (p == null) {
                throw new ApiException(HttpStatus.CONFLICT, "Product is no longer available");
            }
            return p;
        });
    }

    private String token(long userId, String role) {
        return Jwts.builder().subject(String.valueOf(userId)).claim("role", role)
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8))).compact();
    }

    private String user(long id) { return "Bearer " + token(id, "CUSTOMER"); }
    private String admin() { return "Bearer " + token(1, "ADMIN"); }

    private void cartHas(CartInfo.CartItemInfo... items) {
        when(cartClient.getCart(anyString())).thenReturn(new CartInfo(List.of(items)));
    }

    private long placeOrder(long userId) throws Exception {
        cartHas(new CartInfo.CartItemInfo(1L, 2), new CartInfo.CartItemInfo(2L, 1));
        String body = mvc.perform(post("/api/orders").header("Authorization", user(userId))
                        .contentType(MediaType.APPLICATION_JSON).content(CHECKOUT))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    @Test
    void requiresLogin() throws Exception {
        mvc.perform(get("/api/orders")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(CHECKOUT))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void checkoutCreatesOrderFromCartAndClearsIt() throws Exception {
        cartHas(new CartInfo.CartItemInfo(1L, 2), new CartInfo.CartItemInfo(2L, 1));
        mvc.perform(post("/api/orders").header("Authorization", user(10))
                        .contentType(MediaType.APPLICATION_JSON).content(CHECKOUT))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.userId").value(10))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.totalAmount").value(2598.98))
                .andExpect(jsonPath("$.shippingAddress.city").value("Pune"));
        verify(cartClient).clear(anyString());
    }

    @Test
    void checkoutRejectsEmptyCartInactiveProductAndBadAddress() throws Exception {
        when(cartClient.getCart(anyString())).thenReturn(new CartInfo(List.of()));
        mvc.perform(post("/api/orders").header("Authorization", user(11))
                        .contentType(MediaType.APPLICATION_JSON).content(CHECKOUT))
                .andExpect(status().isBadRequest());

        cartHas(new CartInfo.CartItemInfo(3L, 1));
        mvc.perform(post("/api/orders").header("Authorization", user(11))
                        .contentType(MediaType.APPLICATION_JSON).content(CHECKOUT))
                .andExpect(status().isConflict());

        mvc.perform(post("/api/orders").header("Authorization", user(11))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"shippingAddress\":{\"line1\":\"x\"}}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/orders").header("Authorization", user(11))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verify(cartClient, never()).clear(anyString());
    }

    @Test
    void customersSeeOnlyTheirOwnOrdersAndCanCancelPendingOnly() throws Exception {
        long id = placeOrder(20);

        mvc.perform(get("/api/orders").header("Authorization", user(20)))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(get("/api/orders/" + id).header("Authorization", user(20))).andExpect(status().isOk());
        // another user cannot see or cancel it
        mvc.perform(get("/api/orders/" + id).header("Authorization", user(21))).andExpect(status().isNotFound());
        mvc.perform(post("/api/orders/" + id + "/cancel").header("Authorization", user(21)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/orders").header("Authorization", user(21)))
                .andExpect(jsonPath("$", hasSize(0)));

        mvc.perform(post("/api/orders/" + id + "/cancel").header("Authorization", user(20)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(post("/api/orders/" + id + "/cancel").header("Authorization", user(20)))
                .andExpect(status().isConflict());
    }

    @Test
    void adminStatsCountOrdersAndRevenue() throws Exception {
        placeOrder(50);
        mvc.perform(get("/api/admin/orders/stats").header("Authorization", user(50)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/orders/stats").header("Authorization", admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.countByStatus.PENDING").isNumber())
                .andExpect(jsonPath("$.countByStatus.DELIVERED").isNumber())
                .andExpect(jsonPath("$.totalOrders").isNumber())
                .andExpect(jsonPath("$.revenue").isNumber());
    }

    @Test
    void internalStatusEndpointNeedsTheServiceKey() throws Exception {
        long id = placeOrder(40);
        String url = "/internal/orders/" + id + "/status";

        // no key, wrong key, and a user token alone are all rejected
        mvc.perform(put(url).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"PAID\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(put(url).header("X-Internal-Key", "wrong").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"PAID\"}")).andExpect(status().isUnauthorized());
        mvc.perform(put(url).header("Authorization", user(40)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"PAID\"}")).andExpect(status().isUnauthorized());

        mvc.perform(put(url).header("X-Internal-Key", "test-internal-key")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"PAID\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PAID"));
        // the same transition rules apply
        mvc.perform(put(url).header("X-Internal-Key", "test-internal-key")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"PENDING\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void adminMovesOrderThroughValidStatusesOnly() throws Exception {
        long id = placeOrder(30);
        String url = "/api/admin/orders/" + id + "/status";

        // customers cannot use admin endpoints
        mvc.perform(put(url).header("Authorization", user(30)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"PAID\"}")).andExpect(status().isForbidden());

        // cannot skip PENDING -> SHIPPED
        mvc.perform(put(url).header("Authorization", admin()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"SHIPPED\"}")).andExpect(status().isConflict());

        for (String next : new String[] {"PAID", "SHIPPED", "DELIVERED"}) {
            mvc.perform(put(url).header("Authorization", admin()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"" + next + "\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(next));
        }
        // DELIVERED is final
        mvc.perform(put(url).header("Authorization", admin()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"CANCELLED\"}")).andExpect(status().isConflict());

        mvc.perform(get("/api/admin/orders").header("Authorization", admin()).param("status", "DELIVERED"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/admin/orders").header("Authorization", user(30))).andExpect(status().isForbidden());
    }
}
