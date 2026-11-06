package com.endel.demobox.controller;


import com.endel.demobox.authentication.CustomUserDetails;
import com.endel.demobox.authentication.JWTAuthenticationService;
import com.endel.demobox.authentication.LoginRequestDTO;
import com.endel.demobox.exception.InvalidCredentialsException;
import com.endel.demobox.ratelimit.LoginAttemptGuard;
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
    private LoginAttemptGuard loginGuard;

    public AuthController(AuthenticationManager authenticationManager) {
        this.authenticationManager = authenticationManager;
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequestDTO request, HttpServletRequest http) {
        String token = loginGuard.attempt(request.getEmail(), http.getRemoteAddr(), () -> {
            try {
                Authentication authentication = authenticationManager.authenticate(
                        new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));
                CustomUserDetails user = (CustomUserDetails) authentication.getPrincipal();
                // This login is the admin panel's. Customers sign in through /api/account/login, so a
                // customer password here gets the same answer as a wrong one.
                if (user.getAuthorities().stream().noneMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()))) {
                    throw new InvalidCredentialsException("Correo o contraseña incorrectos.");
                }
                return jwtAuthenticationService.generateToken(user);
            } catch (AuthenticationException e) {
                throw new InvalidCredentialsException("Correo o contraseña incorrectos.");
            }
        });
        Map<String, Object> response = new HashMap<>();
        response.put("Response", true);
        response.put("token", token);
        return ResponseEntity.ok(response);
    }

}
