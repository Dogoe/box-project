package com.endel.demobox.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionConfigCheckTest {

    private static final String GOOD_SECRET = "a-long-random-production-secret-0123456789";
    private static final String GOOD_PASSWORD = "una-clave-larga-2026";

    private static ProductionConfigCheck check(String secret, String password, String url) {
        return new ProductionConfigCheck(secret, password, url, false, "", "");
    }

    @Test
    void acceptsRealValues() {
        assertThatCode(() -> check(GOOD_SECRET, GOOD_PASSWORD, "https://baja-solar.onrender.com").afterPropertiesSet())
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsTheDevelopmentJwtSecret() {
        assertThatThrownBy(() -> check(ProductionConfigCheck.DEV_JWT_SECRET, GOOD_PASSWORD, "https://x.com").afterPropertiesSet())
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    void rejectsAShortJwtSecret() {
        assertThatThrownBy(() -> check("short", GOOD_PASSWORD, "https://x.com").afterPropertiesSet())
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    void rejectsTheDefaultAdminPassword() {
        assertThatThrownBy(() -> check(GOOD_SECRET, "admin123", "https://x.com").afterPropertiesSet())
                .hasMessageContaining("ADMIN_BOOTSTRAP_PASSWORD");
    }

    @Test
    void rejectsAMissingOrPlainHttpAddressAndListsEveryProblem() {
        assertThatThrownBy(() -> check("short", "admin123", "").afterPropertiesSet())
                .hasMessageContaining("JWT_SECRET")
                .hasMessageContaining("ADMIN_BOOTSTRAP_PASSWORD")
                .hasMessageContaining("PUBLIC_BASE_URL");
        assertThatThrownBy(() -> check(GOOD_SECRET, GOOD_PASSWORD, "http://x.com").afterPropertiesSet())
                .hasMessageContaining("PUBLIC_BASE_URL");
    }
}
