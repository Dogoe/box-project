package com.endel.demobox.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Real security chain; the test profile has no R2 keys, so uploads report that storage isn't set up. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminImageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken() throws Exception {
        String body = mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@bajasolarsupply.com\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return (String) objectMapper.readValue(body, Map.class).get("token");
    }

    @Test
    void uploadsAreAdminOnly() throws Exception {
        mockMvc.perform(multipart("/api/admin/images").file(new MockMultipartFile("file", "a.png", "image/png", new byte[]{1})))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/images/status")).andExpect(status().isForbidden());
    }

    @Test
    void withoutR2KeysTheAdminIsToldToPasteLinks() throws Exception {
        String token = adminToken();
        mockMvc.perform(get("/api/admin/images/status").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        byte[] png = java.util.Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==");
        mockMvc.perform(multipart("/api/admin/images").file(new MockMultipartFile("file", "a.png", "image/png", png))
                        .param("folder", "products")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value(containsString("pegar el link")));
    }

    @Test
    void notAnImageIsA400() throws Exception {
        mockMvc.perform(multipart("/api/admin/images").file(new MockMultipartFile("file", "a.txt", "text/plain", "hola".getBytes()))
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("no es una imagen válida")));
    }
}
