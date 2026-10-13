package com.ecommerce.catalog;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CatalogFlowTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Value("${app.jwt.secret}") String secret;

    private String token(String role) {
        return Jwts.builder().subject("1").claim("role", role)
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8))).compact();
    }

    private String bearer(String role) {
        return "Bearer " + token(role);
    }

    @Test
    void adminManagesCatalogAndPublicBrowses() throws Exception {
        // writes: 401 without token, 403 for a customer
        String cat = "{\"name\":\"Laptops\",\"description\":\"Portable computers\"}";
        mvc.perform(post("/api/categories").contentType(MediaType.APPLICATION_JSON).content(cat))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/categories").header("Authorization", bearer("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON).content(cat))
                .andExpect(status().isForbidden());

        // admin creates category
        String catBody = mvc.perform(post("/api/categories").header("Authorization", bearer("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(cat))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long categoryId = json.readTree(catBody).get("id").asLong();

        mvc.perform(post("/api/categories").header("Authorization", bearer("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(cat))
                .andExpect(status().isConflict());

        // admin creates product
        String product = "{\"name\":\"UltraBook 14\",\"description\":\"Light laptop\",\"sku\":\"UB-14\","
                + "\"price\":999.99,\"categoryId\":" + categoryId + "}";
        String prodBody = mvc.perform(post("/api/products").header("Authorization", bearer("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(product))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.active").value(true))
                .andReturn().getResponse().getContentAsString();
        long productId = json.readTree(prodBody).get("id").asLong();

        // duplicate SKU
        mvc.perform(post("/api/products").header("Authorization", bearer("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(product))
                .andExpect(status().isConflict());

        // public browse, search and filter (no token)
        mvc.perform(get("/api/products").param("q", "ultra"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(1)));
        mvc.perform(get("/api/products").param("categoryId", String.valueOf(categoryId)).param("maxPrice", "500"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(0)));
        mvc.perform(get("/api/products/" + productId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.categoryName").value("Laptops"));

        // category with products cannot be deleted
        mvc.perform(delete("/api/categories/" + categoryId).header("Authorization", bearer("ADMIN")))
                .andExpect(status().isConflict());

        // soft delete hides the product
        mvc.perform(delete("/api/products/" + productId).header("Authorization", bearer("ADMIN")))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/products/" + productId)).andExpect(status().isNotFound());
        mvc.perform(get("/api/products")).andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    void adminSeesInactiveProductsAndCanReactivateThem() throws Exception {
        String catBody = mvc.perform(post("/api/categories").header("Authorization", bearer("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Audio\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long categoryId = json.readTree(catBody).get("id").asLong();
        String product = "{\"name\":\"Headphones\",\"sku\":\"HP-1\",\"price\":59.00,\"categoryId\":" + categoryId + "}";
        String prodBody = mvc.perform(post("/api/products").header("Authorization", bearer("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(product))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = json.readTree(prodBody).get("id").asLong();
        mvc.perform(delete("/api/products/" + id).header("Authorization", bearer("ADMIN")))
                .andExpect(status().isNoContent());

        // hidden from the public, still visible to admins
        mvc.perform(get("/api/products/" + id)).andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/products").header("Authorization", bearer("ADMIN")).param("active", "false")
                        .param("q", "hp-1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].active").value(false));
        mvc.perform(get("/api/admin/products/" + id).header("Authorization", bearer("ADMIN")))
                .andExpect(status().isOk());

        // customers and anonymous callers cannot use the admin endpoints
        mvc.perform(get("/api/admin/products")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/products").header("Authorization", bearer("CUSTOMER")))
                .andExpect(status().isForbidden());

        // re-activate through the normal update
        String reactivate = "{\"name\":\"Headphones\",\"sku\":\"HP-1\",\"price\":59.00,\"categoryId\":" + categoryId
                + ",\"active\":true}";
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/products/" + id)
                        .header("Authorization", bearer("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(reactivate))
                .andExpect(status().isOk());
        mvc.perform(get("/api/products/" + id)).andExpect(status().isOk());

        // tests share one database: leave the catalog empty of active products for the other test
        mvc.perform(delete("/api/products/" + id).header("Authorization", bearer("ADMIN")))
                .andExpect(status().isNoContent());
    }

    @Test
    void validatesInput() throws Exception {
        mvc.perform(post("/api/products").header("Authorization", bearer("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"sku\":\"\",\"price\":0}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/products").param("sortBy", "password")).andExpect(status().isBadRequest());
    }
}
