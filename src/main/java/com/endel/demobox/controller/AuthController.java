package com.endel.demobox.controller;


import com.endel.demobox.authentication.CustomUserDetails;
import com.endel.demobox.authentication.JWTAuthenticationService;
import com.endel.demobox.authentication.LoginRequestDTO;
import com.endel.demobox.authentication.RegisterRequestDTO;
import com.endel.demobox.service.UserService;
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

    public AuthController(AuthenticationManager authenticationManager) {
        this.authenticationManager = authenticationManager;
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequestDTO request) {
        Map<String, Object> response = new HashMap<>();
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getEmail(),
                            request.getPassword()
                    )
            );

            CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
            String token = jwtAuthenticationService.generateToken(userDetails);

            response.put("Response",true);
            response.put("token",token);
            return ResponseEntity.ok(response);
        } catch (AuthenticationException e) {
            throw new RuntimeException("Invalid username or password");
        }

    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody RegisterRequestDTO registerRequest) {
        Map<String, Object> response = new HashMap<>();
        userService.registerUser(registerRequest);
        response.put("Response","User registered successfully");
        return ResponseEntity.ok(response);
    }
}
