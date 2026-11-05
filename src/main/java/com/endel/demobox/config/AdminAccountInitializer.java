package com.endel.demobox.config;

import com.endel.demobox.model.User;
import com.endel.demobox.repository.UserRepository;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * Customer accounts never get ROLE_ADMIN, so this is the only way to get into the admin panel:
 * on startup it creates the admin from admin.bootstrap.email / .password if that user doesn't
 * exist yet. It never changes an existing admin. In production both values are required env vars
 * (ADMIN_BOOTSTRAP_EMAIL / ADMIN_BOOTSTRAP_PASSWORD).
 */
@Component
@Log4j2
public class AdminAccountInitializer implements CommandLineRunner {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;
    private final String adminPassword;

    public AdminAccountInitializer(UserRepository userRepository,
                                    PasswordEncoder passwordEncoder,
                                    @Value("${admin.bootstrap.email}") String adminEmail,
                                    @Value("${admin.bootstrap.password}") String adminPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(String... args) {
        if (userRepository.existsByUsername(adminEmail)) {
            return;
        }

        User admin = new User();
        admin.setEmail(adminEmail);
        admin.setUsername(adminEmail);
        admin.setPassword(passwordEncoder.encode(adminPassword));
        admin.setRoles(new HashSet<>(Set.of("ROLE_ADMIN")));
        userRepository.save(admin);

        log.info("Created the admin account ({}).", adminEmail);
    }
}
