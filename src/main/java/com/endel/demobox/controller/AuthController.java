package com.endel.demobox.controller;


import com.endel.demobox.authentication.CustomUserDetails;
import com.endel.demobox.authentication.JWTAuthenticationService;
import com.endel.demobox.authentication.LoginRequestDTO;
import com.endel.demobox.authentication.RegisterRequestDTO;
import com.endel.demobox.exception.InvalidCredentialsException;
import com.endel.demobox.ratelimit.LoginAttemptGuard;
import com.endel.demobox.ratelimit.RateLimit;
import com.endel.demobox.ratelimit.RateLimiter;
import com.endel.demobox.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;

    @Autowired
    private JWTAuthenticationService jwtAuthenticationService;

    @Autowired
    private UserService userService;

    @Autowired
    private LoginAttemptGuard loginGuard;

    @Autowired
    private RateLimiter rateLimiter;

    public AuthController(AuthenticationManager authenticationManager) {
        this.authenticationManager = authenticationManager;
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequestDTO request, HttpServletRequest http) {
        String token = loginGuard.attempt(request.getEmail(), http.getRemoteAddr(), () -> {
            try {
                Authentication authentication = authenticationManager.authenticate(
                        new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));
                return jwtAuthenticationService.generateToken((CustomUserDetails) authentication.getPrincipal());
            } catch (AuthenticationException e) {
                throw new InvalidCredentialsException("Correo o contraseña incorrectos.");
            }
        });
        Map<String, Object> response = new HashMap<>();
        response.put("Response", true);
        response.put("token", token);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody RegisterRequestDTO registerRequest, HttpServletRequest http) {
        rateLimiter.consume(RateLimit.REGISTER_PER_IP, http.getRemoteAddr());
        Map<String, Object> response = new HashMap<>();
        userService.registerUser(registerRequest);
        response.put("Response","User registered successfully");
        return ResponseEntity.ok(response);
    }
}
