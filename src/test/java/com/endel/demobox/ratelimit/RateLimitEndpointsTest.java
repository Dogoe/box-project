package com.endel.demobox.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The limits through the real HTTP stack: 429 with a readable message and Retry-After. Each test uses its own IP. */
@SpringBootTest(properties = "app.rate-limit.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RateLimitEndpointsTest {

    @Autowired
    private MockMvc mockMvc;

    private static MockHttpServletRequestBuilder json(String url, String body, String ip) {
        return post(url).contentType(MediaType.APPLICATION_JSON).content(body)
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                });
    }

    @Test
    void customerLoginLocksTheEmailAfterFiveWrongPasswords() throws Exception {
        mockMvc.perform(json("/api/account/register",
                        "{\"fullName\":\"Juan\",\"phone\":\"6461234567\",\"email\":\"lock@ejemplo.com\",\"password\":\"secreta123\"}", "10.1.0.1"))
                .andExpect(status().isCreated());
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(json("/api/account/login", "{\"email\":\"lock@ejemplo.com\",\"password\":\"mala\"}", "10.1.0.1"))
                    .andExpect(status().isUnauthorized());
        }
        // Even the right password, from another IP, waits until the lock ends.
        mockMvc.perform(json("/api/account/login", "{\"email\":\"lock@ejemplo.com\",\"password\":\"secreta123\"}", "10.1.0.2"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "900"))
                .andExpect(jsonPath("$.message").value(
                        "Demasiados intentos fallidos con este correo. Si no recuerdas tu contraseña, restablécela. Intenta de nuevo en 15 minutos."));
    }

    @Test
    void adminLoginIsLimitedTooAndAnswers401InsteadOf500() throws Exception {
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(json("/auth/login", "{\"email\":\"admin@bajasolarsupply.com\",\"password\":\"mala\"}", "10.2.0." + i))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(json("/auth/login", "{\"email\":\"admin@bajasolarsupply.com\",\"password\":\"mala\"}", "10.2.0.9"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void forgotPasswordSendsAtMostThreeEmailsPerHourToOneAddress() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(json("/api/account/forgot-password", "{\"email\":\"nadie@ejemplo.com\"}", "10.3.0." + i))
                    .andExpect(status().isNoContent());
        }
        mockMvc.perform(json("/api/account/forgot-password", "{\"email\":\"Nadie@ejemplo.com\"}", "10.3.0.9"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value(containsString("Intenta de nuevo en 60 minutos")));
    }

    @Test
    void emailLookupsFromCheckoutAreCappedPerIp() throws Exception {
        for (int i = 0; i < 30; i++) {
            mockMvc.perform(json("/api/account/email-status", "{\"email\":\"user" + i + "@ejemplo.com\"}", "10.4.0.1"))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(json("/api/account/email-status", "{\"email\":\"otro@ejemplo.com\"}", "10.4.0.1"))
                .andExpect(status().isTooManyRequests());
        mockMvc.perform(json("/api/account/email-status", "{\"email\":\"otro@ejemplo.com\"}", "10.4.0.2"))
                .andExpect(status().isOk());
    }
}
