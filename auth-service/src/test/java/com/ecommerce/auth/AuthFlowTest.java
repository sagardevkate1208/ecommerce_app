package com.ecommerce.auth;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthFlowTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private static final String REGISTER = """
            {"email":"jane@example.com","password":"password123","firstName":"Jane","lastName":"Doe"}""";

    @Test
    void registerLoginAndReadProfile() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(REGISTER))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token", notNullValue()));

        // duplicate email
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(REGISTER))
                .andExpect(status().isConflict());

        // wrong password
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"jane@example.com\",\"password\":\"wrong-pass\"}"))
                .andExpect(status().isUnauthorized());

        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"jane@example.com\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = json.readTree(body).get("token").asText();

        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("jane@example.com"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"));

        mvc.perform(post("/api/users/me/addresses").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"line1\":\"1 Main St\",\"city\":\"Pune\",\"state\":\"MH\","
                                + "\"postalCode\":\"411001\",\"country\":\"IN\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.defaultAddress").value(true));
    }

    @Test
    void profileRequiresToken() throws Exception {
        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void registerValidatesInput() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"bad\",\"password\":\"short\",\"firstName\":\"\",\"lastName\":\"\"}"))
                .andExpect(status().isBadRequest());
    }
}
