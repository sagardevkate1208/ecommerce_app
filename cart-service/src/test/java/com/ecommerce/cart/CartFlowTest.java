package com.ecommerce.cart;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecommerce.cart.client.CatalogClient;
import com.ecommerce.cart.dto.ProductInfo;
import com.ecommerce.cart.exception.ApiException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Date;
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
class CartFlowTest {

    @Autowired MockMvc mvc;
    @Value("${app.jwt.secret}") String secret;
    @MockitoBean CatalogClient catalog;

    @BeforeEach
    void stubCatalog() {
        Map<Long, ProductInfo> products = Map.of(
                1L, new ProductInfo(1L, "UltraBook 14", "UB-14", new BigDecimal("999.99"), null, true),
                2L, new ProductInfo(2L, "Pixel Phone", "PH-1", new BigDecimal("599.00"), null, true),
                3L, new ProductInfo(3L, "Retired", "OLD-1", new BigDecimal("10.00"), null, false));
        when(catalog.getProduct(anyLong())).thenAnswer(inv -> {
            ProductInfo p = products.get(inv.<Long>getArgument(0));
            if (p == null) {
                throw new ApiException(HttpStatus.NOT_FOUND, "Product not found");
            }
            return p;
        });
    }

    private String bearer(long userId) {
        return "Bearer " + Jwts.builder().subject(String.valueOf(userId)).claim("role", "CUSTOMER")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8))).compact();
    }

    private String add(long productId, int qty) {
        return "{\"productId\":" + productId + ",\"quantity\":" + qty + "}";
    }

    @Test
    void requiresLogin() throws Exception {
        mvc.perform(get("/api/cart")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/cart/items").contentType(MediaType.APPLICATION_JSON).content(add(1, 1)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void addUpdateRemoveAndClear() throws Exception {
        long user = 101;
        mvc.perform(get("/api/cart").header("Authorization", bearer(user)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.totalItems").value(0));

        mvc.perform(post("/api/cart/items").header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON).content(add(1, 2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].productName").value("UltraBook 14"))
                .andExpect(jsonPath("$.items[0].lineTotal").value(1999.98));

        // adding the same product again increments the quantity
        mvc.perform(post("/api/cart/items").header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON).content(add(1, 1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].quantity").value(3));

        mvc.perform(post("/api/cart/items").header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON).content(add(2, 1)))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.totalItems").value(4))
                .andExpect(jsonPath("$.subtotal").value(3598.97));

        mvc.perform(put("/api/cart/items/1").header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subtotal").value(1598.99));

        mvc.perform(delete("/api/cart/items/2").header("Authorization", bearer(user)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items", hasSize(1)));
        mvc.perform(delete("/api/cart/items/2").header("Authorization", bearer(user)))
                .andExpect(status().isNotFound());

        mvc.perform(delete("/api/cart").header("Authorization", bearer(user))).andExpect(status().isNoContent());
        mvc.perform(get("/api/cart").header("Authorization", bearer(user)))
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void cartsAreSeparatePerUser() throws Exception {
        mvc.perform(post("/api/cart/items").header("Authorization", bearer(201))
                .contentType(MediaType.APPLICATION_JSON).content(add(1, 1))).andExpect(status().isCreated());
        mvc.perform(get("/api/cart").header("Authorization", bearer(202)))
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void rejectsBadInput() throws Exception {
        long user = 301;
        mvc.perform(post("/api/cart/items").header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON).content(add(1, 0)))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/cart/items").header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON).content(add(1, 100)))
                .andExpect(status().isBadRequest());
        // unknown and inactive products
        mvc.perform(post("/api/cart/items").header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON).content(add(999, 1)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/cart/items").header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON).content(add(3, 1)))
                .andExpect(status().isNotFound());
        // quantity cap when incrementing
        mvc.perform(post("/api/cart/items").header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON).content(add(1, 99))).andExpect(status().isCreated());
        mvc.perform(post("/api/cart/items").header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON).content(add(1, 1)))
                .andExpect(status().isBadRequest());
        // updating an item that is not in the cart
        mvc.perform(put("/api/cart/items/2").header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":1}"))
                .andExpect(status().isNotFound());
    }
}
