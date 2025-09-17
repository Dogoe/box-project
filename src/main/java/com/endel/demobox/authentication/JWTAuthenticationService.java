package com.endel.demobox.authentication;

import com.endel.demobox.model.User;
import com.endel.demobox.service.UserService;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import io.micrometer.common.util.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

@Service
public class JWTAuthenticationService implements AuthenticationService{
    //private final Key secretKey;
    private final long expirationMs;
    private final SecretKey secretKey ;

    private final UserService userService;

    public JWTAuthenticationService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-ms}") long expirationMs, UserService userService) {
        this.userService = userService;

        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) { // 256 bits = 32 bytes
            //throw new Exception("JWT secret key must be at least 256 bits (32 bytes)");
        }

        this.secretKey = Keys.hmacShaKeyFor(secretBytes);
        this.expirationMs = expirationMs;
    }

    @Override
    public User authenticate(String token) {
        //final Key key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(token));
        Jws<Claims> jws = null;
        try {
            if (StringUtils.isNotEmpty(token)) {
                jws = Jwts.parser()
                        .verifyWith(secretKey) // ✅ replaces setSigningKey
                        .build()
                        .parseSignedClaims(token);
            }

        } catch (Exception e) {
            // Invalid token, optionally clear context or reject request
        }

        if (jws != null) {
            Claims claims = jws.getPayload();
            if (!claims.isEmpty()) {
                String username = claims.getSubject();
                if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    User user = userService.findByUsername(username);
                    return user;
                }
            }
        }



        return null;
    }

    @Override
    public User createAnonymousUserContent(String uidAnonymous) {
        return null;
    }

    @Override
    public String generateToken(String subject, Map<String, ?> claims, Date issuedAt, Date expiresAt) {
        JwtBuilder jwtBuilder = Jwts.builder();

        // Token claims
        if (StringUtils.isNotBlank(subject)) {
            jwtBuilder = jwtBuilder.subject(subject);
        }
        if (claims != null) {
            jwtBuilder = jwtBuilder.claims(claims);
        }

        // Issue and expiration dates
        final Date createdDate = issuedAt != null ? issuedAt : new Date();
        final Date expirationDate = expiresAt != null ? expiresAt : new Date(createdDate.getTime() + expirationMs);
        jwtBuilder = jwtBuilder.issuedAt(createdDate);
        jwtBuilder = jwtBuilder.expiration(expirationDate);

        // Token signature
        //final String secretToken = secretKey;
        //final SecretKey secretKey = Keys.hmacShaKeyFor(secretToken.getBytes(StandardCharsets.UTF_8));
        //jwtBuilder = jwtBuilder.signWith(secretKey, Jwts.SIG.HS256);
        jwtBuilder = jwtBuilder.signWith(secretKey);

        // Compression
        jwtBuilder = jwtBuilder.compressWith(Jwts.ZIP.GZIP);

        return jwtBuilder.compact();
    }

    @Override
    public String generateToken(CustomUserDetails userDetails) {

        JwtBuilder jwtBuilder = Jwts.builder();

        // Token claims
        if (StringUtils.isNotBlank(userDetails.getUsername())) {
            jwtBuilder = jwtBuilder.subject(userDetails.getUsername());
        }
        if (userDetails.getAuthorities() != null) {
            jwtBuilder = jwtBuilder.claim("roles",userDetails.getAuthorities());
        }

        // Issue and expiration dates
        jwtBuilder.issuedAt(new Date());
        jwtBuilder.expiration(new Date(System.currentTimeMillis() + expirationMs));
        // Token signature
        //final String secretToken = secretKey;
       // final SecretKey secretKey = Keys.hmacShaKeyFor(secretToken.getBytes(StandardCharsets.UTF_8));
        jwtBuilder = jwtBuilder.signWith(secretKey);

        // Compression
        jwtBuilder = jwtBuilder.compressWith(Jwts.ZIP.GZIP);

        return jwtBuilder.compact();
    }
}
