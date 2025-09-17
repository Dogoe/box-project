package com.endel.demobox.authentication;

import com.endel.demobox.model.User;

import java.util.Date;
import java.util.Map;

public interface AuthenticationService {
    //long EXPIRATION_TIME = 43_200_000; // 12 hours
    User authenticate(String token);

    User createAnonymousUserContent(String uidAnonymous);
    String generateToken(String subject, Map<String, ?> claims, Date issuedAt, Date expiresAt);

    String generateToken(CustomUserDetails userDetails);

}
