package com.endel.demobox.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the real security filter chain (unlike most controller tests in this project,
 * which disable it) specifically to verify the GET-public / PUT-admin-only split configured
 * in JwtConfig for this one path actually behaves as intended.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ExchangeRateControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken() throws Exception {
        String body = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@bajasolarsupply.com\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        @SuppressWarnings("unchecked")
        Map<String, Object> response = objectMapper.readValue(body, Map.class);
        return (String) response.get("token");
    }

    @Test
    void get_isPublicAndReturnsTheCurrentRate() throws Exception {
        mockMvc.perform(get("/api/settings/exchange-rate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usdToMxnRate").exists());
    }

    @Test
    void put_isRejectedWithoutAnAdminToken() throws Exception {
        mockMvc.perform(put("/api/settings/exchange-rate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usdToMxnRate\": 20.00}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void put_updatesTheRateWhenAuthenticatedAsAdmin() throws Exception {
        String token = adminToken();

        mockMvc.perform(put("/api/settings/exchange-rate")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usdToMxnRate\": 21.75}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usdToMxnRate").value(21.75));

        mockMvc.perform(get("/api/settings/exchange-rate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usdToMxnRate").value(21.75));
    }

    @Test
    void put_rejectsANonPositiveRate() throws Exception {
        String token = adminToken();

        mockMvc.perform(put("/api/settings/exchange-rate")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usdToMxnRate\": 0}"))
                .andExpect(status().isBadRequest());
    }
}
