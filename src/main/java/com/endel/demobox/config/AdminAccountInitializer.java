package com.endel.demobox.config;

import com.endel.demobox.model.User;
import com.endel.demobox.repository.UserRepository;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Self-registration always assigns ROLE_USER, so there is otherwise no way to
 * reach the catalog admin panel. Seeds a default admin account on startup if
 * one doesn't already exist. Override admin.bootstrap.password via an
 * environment variable before deploying anywhere the default would matter.
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
        admin.setRoles(Set.of("ROLE_ADMIN"));
        userRepository.save(admin);

        log.info("Created default admin account ({}). Override ADMIN_BOOTSTRAP_PASSWORD before deploying anywhere real.", adminEmail);
    }
}
