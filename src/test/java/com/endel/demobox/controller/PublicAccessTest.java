package com.endel.demobox.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Public entry points; /auth/login is the admin panel's login, so only admins get a token there. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PublicAccessTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void adminGetsAToken() throws Exception {
        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@bajasolarsupply.com\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void customerWithTheRightPasswordIsTurnedAwayLikeAWrongPassword() throws Exception {
        mockMvc.perform(post("/api/account/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Ana\",\"phone\":\"6461234567\",\"email\":\"ana@ejemplo.com\",\"password\":\"secreta123\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ana@ejemplo.com\",\"password\":\"secreta123\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Correo o contraseña incorrectos."));
    }

    @Test
    void theBareDomainOpensTheStore() throws Exception {
        mockMvc.perform(get("/")).andExpect(redirectedUrl("/home"));
    }

    @Test
    void theOldSelfRegistrationEndpointIsGone() throws Exception {
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"x@ejemplo.com\",\"password\":\"secreta123\"}"))
                .andExpect(status().isForbidden());
    }
}
