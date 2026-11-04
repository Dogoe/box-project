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
 * which disable it) specifically to verify the profit margin endpoint is admin-only for
 * BOTH GET and PUT - unlike exchange-rate, this is internal business data, not something
 * client-side storefront code needs to read.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProfitMarginControllerTest {

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
    void get_isRejectedWithoutAnAdminToken() throws Exception {
        mockMvc.perform(get("/api/settings/profit-margin"))
                .andExpect(status().isForbidden());
    }

    @Test
    void put_isRejectedWithoutAnAdminToken() throws Exception {
        mockMvc.perform(put("/api/settings/profit-margin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"marginPercent\": 20.00}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void get_returnsTheCurrentMarginWhenAuthenticatedAsAdmin() throws Exception {
        String token = adminToken();

        mockMvc.perform(get("/api/settings/profit-margin")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marginPercent").exists());
    }

    @Test
    void put_updatesTheMarginWhenAuthenticatedAsAdmin() throws Exception {
        String token = adminToken();

        mockMvc.perform(put("/api/settings/profit-margin")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"marginPercent\": 22.50}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marginPercent").value(22.50));

        mockMvc.perform(get("/api/settings/profit-margin")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marginPercent").value(22.50));
    }

    @Test
    void put_rejectsANegativeMargin() throws Exception {
        String token = adminToken();

        mockMvc.perform(put("/api/settings/profit-margin")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"marginPercent\": -5}"))
                .andExpect(status().isBadRequest());
    }
}
