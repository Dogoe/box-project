package com.endel.demobox.config;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Refuses to start production with values that are only fit for development: the public JWT
 * secret from git, the default admin password, or a site address without https. Missing optional
 * services (photo storage, email) only log a warning, since the store works without them.
 */
@Component
@Profile("prod")
@Log4j2
public class ProductionConfigCheck implements InitializingBean {

    /** The development secret committed in application.yml; anyone can read it. */
    static final String DEV_JWT_SECRET = "K0zDf0M5GyPlZtX7RhV0I5uPzZsZ3YgYV7Z7XgWyfL0=";

    private final String jwtSecret;
    private final String adminPassword;
    private final String publicBaseUrl;
    private final boolean mailEnabled;
    private final String mailUsername;
    private final String r2Endpoint;

    public ProductionConfigCheck(@Value("${jwt.secret}") String jwtSecret,
                                 @Value("${admin.bootstrap.password}") String adminPassword,
                                 @Value("${app.public-base-url}") String publicBaseUrl,
                                 @Value("${app.mail.enabled:true}") boolean mailEnabled,
                                 @Value("${spring.mail.username:}") String mailUsername,
                                 @Value("${app.storage.endpoint:}") String r2Endpoint) {
        this.jwtSecret = jwtSecret;
        this.adminPassword = adminPassword;
        this.publicBaseUrl = publicBaseUrl;
        this.mailEnabled = mailEnabled;
        this.mailUsername = mailUsername;
        this.r2Endpoint = r2Endpoint;
    }

    @Override
    public void afterPropertiesSet() {
        List<String> problems = new ArrayList<>();
        if (jwtSecret.length() < 32 || jwtSecret.equals(DEV_JWT_SECRET)) {
            problems.add("JWT_SECRET must be your own random value of at least 32 characters "
                    + "(e.g. `openssl rand -base64 48`), not the development one.");
        }
        if (adminPassword.length() < 12 || adminPassword.equals("admin123")) {
            problems.add("ADMIN_BOOTSTRAP_PASSWORD must be at least 12 characters and not the default.");
        }
        if (!publicBaseUrl.startsWith("https://")) {
            problems.add("PUBLIC_BASE_URL must be the site's https:// address (got '" + publicBaseUrl + "').");
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Production configuration is not safe:\n - " + String.join("\n - ", problems));
        }

        if (r2Endpoint.isBlank()) {
            log.warn("R2_ENDPOINT is not set: photo uploads are off (the admin can still paste image links).");
        }
        if (mailEnabled && mailUsername.isBlank()) {
            log.warn("MAIL_USERNAME is not set: order emails will fail. Set MAIL_ENABLED=false to skip them on purpose.");
        }
    }
}
