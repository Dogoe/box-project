package com.endel.demobox.authentication;

import com.endel.demobox.model.User;
import com.endel.demobox.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Service;
import org.springframework.web.filter.OncePerRequestFilter;


import java.io.IOException;

@Service
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    final JWTAuthenticationService jwtAuthenticationService;

    final UserRepository userRepository;

    @Autowired
    public JwtAuthenticationFilter(UserRepository userRepository, JWTAuthenticationService jwtAuthenticationService) {
        this.userRepository = userRepository;
        this.jwtAuthenticationService = jwtAuthenticationService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            User user = jwtAuthenticationService.authenticate(token);
            if (user != null) {
                CustomUserDetails userDetails = new CustomUserDetails(user);
                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);
            }


        }
        chain.doFilter(request, response);
    }
}