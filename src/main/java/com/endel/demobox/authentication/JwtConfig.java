package com.endel.demobox.authentication;

import com.endel.demobox.repository.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class JwtConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // disable CSRF

                .cors(cors -> {}) // enable CORS with default settings

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/login",
                                // Only the health check; prod exposes no other actuator endpoint anyway.
                                "/actuator/health",
                                "/v3/api-docs/**",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/",
                                "/home/**",
                                "/cotizacion",
                                "/kits/**",
                                "/admin/**",
                                "/css/**",
                                "/js/**",
                                "/images/**",
                                "/api/quotes/calculate",
                                "/api/quotes/calculate/pdf",
                                "/api/quotes/appliance-presets",
                                "/carrito",
                                "/pedido/**",
                                "/cuenta",
                                "/cuenta/**",
                                "/api/account/email-status",
                                "/api/account/register",
                                "/api/account/login",
                                "/api/account/forgot-password",
                                "/api/account/reset-password",
                                // Spring forwards error responses here; securing it turns every error into a 403.
                                "/error").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/settings/exchange-rate").permitAll()
                        .requestMatchers(HttpMethod.PUT, "/api/settings/exchange-rate").hasRole("ADMIN")
                        .requestMatchers("/api/settings/profit-margin").hasRole("ADMIN")
                        .requestMatchers("/api/settings/wiring").hasRole("ADMIN")
                        .requestMatchers("/api/settings/store").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/orders").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/catalog/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )

                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public UserDetailsService userDetailsService(UserRepository userRepository) {
        return username -> userRepository.findByUsername(username)
                .map(CustomUserDetails::new)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}