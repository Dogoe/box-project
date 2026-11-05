package com.endel.demobox.service.account;

import com.endel.demobox.authentication.CustomUserDetails;
import com.endel.demobox.authentication.JWTAuthenticationService;
import com.endel.demobox.exception.BadRequestException;
import com.endel.demobox.exception.ConflictException;
import com.endel.demobox.exception.InvalidCredentialsException;
import com.endel.demobox.model.User;
import com.endel.demobox.model.UserToken;
import com.endel.demobox.model.dto.account.AccountDto;
import com.endel.demobox.model.dto.account.AuthResponse;
import com.endel.demobox.model.dto.account.RegisterAccountRequest;
import com.endel.demobox.model.dto.account.UpdateAccountRequest;
import com.endel.demobox.repository.UserRepository;
import com.endel.demobox.repository.UserTokenRepository;
import com.endel.demobox.service.PhoneNumbers;
import com.endel.demobox.service.order.OrderService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.HashSet;
import java.util.Set;

/** Shop customer accounts: sign-up, login, profile, email verification and password reset. */
@Service
public class AccountService {
    static final Duration VERIFY_EMAIL_TTL = Duration.ofHours(48);
    static final Duration RESET_PASSWORD_TTL = Duration.ofHours(1);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final UserTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JWTAuthenticationService jwtService;
    private final OrderService orderService;
    private final ApplicationEventPublisher events;

    public AccountService(UserRepository userRepository, UserTokenRepository tokenRepository, PasswordEncoder passwordEncoder,
                          AuthenticationManager authenticationManager, JWTAuthenticationService jwtService,
                          OrderService orderService, ApplicationEventPublisher events) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.orderService = orderService;
        this.events = events;
    }

    /** Accounts use the email as username; normalized so "Juan@Mail.com" and "juan@mail.com" are the same account. */
    public static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    public boolean isRegistered(String email) {
        return userRepository.existsByUsername(normalizeEmail(email));
    }

    public Optional<User> findByEmail(String email) {
        return userRepository.findByUsername(normalizeEmail(email));
    }

    @Transactional
    public AuthResponse register(RegisterAccountRequest request) {
        String email = normalizeEmail(request.getEmail());
        if (userRepository.existsByUsername(email)) {
            throw new ConflictException("Este correo ya tiene cuenta. Inicia sesión.");
        }
        String phone = PhoneNumbers.normalizeMexican(request.getPhone())
                .orElseThrow(() -> new BadRequestException("El teléfono debe tener 10 dígitos."));

        User user = new User();
        user.setUsername(email);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRoles(new HashSet<>(Set.of("ROLE_USER")));
        user.setFullName(request.getFullName().trim());
        user.setPhone(phone);
        user.setEmailVerified(false);
        user = userRepository.save(user);

        sendLink(user, UserToken.Type.VERIFY_EMAIL, VERIFY_EMAIL_TTL);
        return authenticated(user);
    }

    public AuthResponse login(String email, String password) {
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(normalizeEmail(email), password));
        } catch (AuthenticationException e) {
            throw new InvalidCredentialsException("Correo o contraseña incorrectos.");
        }
        return authenticated(findByEmail(email).orElseThrow());
    }

    public AccountDto toDto(User user) {
        AccountDto dto = new AccountDto();
        dto.setFullName(user.getFullName());
        dto.setPhone(user.getPhone());
        dto.setEmail(user.getEmail());
        dto.setEmailVerified(user.isEmailVerified());
        dto.setDefaultStreet(user.getDefaultStreet());
        dto.setDefaultNeighborhood(user.getDefaultNeighborhood());
        dto.setDefaultPostalCode(user.getDefaultPostalCode());
        dto.setDefaultAddressReferences(user.getDefaultAddressReferences());
        return dto;
    }

    @Transactional
    public AccountDto update(User user, UpdateAccountRequest request) {
        user.setFullName(request.getFullName().trim());
        user.setPhone(PhoneNumbers.normalizeMexican(request.getPhone())
                .orElseThrow(() -> new BadRequestException("El teléfono debe tener 10 dígitos.")));
        user.setDefaultStreet(blankToNull(request.getDefaultStreet()));
        user.setDefaultNeighborhood(blankToNull(request.getDefaultNeighborhood()));
        user.setDefaultPostalCode(blankToNull(request.getDefaultPostalCode()));
        user.setDefaultAddressReferences(blankToNull(request.getDefaultAddressReferences()));
        return toDto(userRepository.save(user));
    }

    @Transactional
    public void resendVerification(User user) {
        if (!user.isEmailVerified()) {
            sendLink(user, UserToken.Type.VERIFY_EMAIL, VERIFY_EMAIL_TTL);
        }
    }

    /** @return how many earlier guest orders were attached to the account, or empty if the link is invalid/expired/used. */
    @Transactional
    public Optional<Integer> verifyEmail(String rawToken) {
        return consume(rawToken, UserToken.Type.VERIFY_EMAIL).map(this::markEmailVerified);
    }

    /** Always succeeds from the caller's view, so the response doesn't reveal anything extra. */
    @Transactional
    public void forgotPassword(String email) {
        findByEmail(email).ifPresent(user -> sendLink(user, UserToken.Type.RESET_PASSWORD, RESET_PASSWORD_TTL));
    }

    @Transactional
    public AuthResponse resetPassword(String rawToken, String newPassword) {
        User user = consume(rawToken, UserToken.Type.RESET_PASSWORD)
                .orElseThrow(() -> new BadRequestException("El link para restablecer tu contraseña no es válido o ya expiró. Pide uno nuevo."));
        user.setPassword(passwordEncoder.encode(newPassword));
        // Opening a link sent to the inbox proves ownership of the email too.
        markEmailVerified(user);
        return authenticated(user);
    }

    private int markEmailVerified(User user) {
        user.setEmailVerified(true);
        userRepository.save(user);
        return orderService.claimGuestOrders(user.getId(), user.getEmail());
    }

    private AuthResponse authenticated(User user) {
        return new AuthResponse(jwtService.generateToken(new CustomUserDetails(user)), toDto(user));
    }

    private void sendLink(User user, UserToken.Type type, Duration ttl) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        UserToken token = new UserToken();
        token.setUserId(user.getId());
        token.setType(type);
        token.setTokenHash(sha256(rawToken));
        token.setExpiresAt(LocalDateTime.now().plus(ttl));
        tokenRepository.save(token);

        events.publishEvent(new AccountEmailEvent(
                type == UserToken.Type.VERIFY_EMAIL ? AccountEmailEvent.Type.VERIFY_EMAIL : AccountEmailEvent.Type.RESET_PASSWORD,
                user.getEmail(), user.getFullName(), rawToken));
    }

    private Optional<User> consume(String rawToken, UserToken.Type type) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        return tokenRepository.findByTokenHash(sha256(rawToken))
                .filter(token -> token.getType() == type)
                .filter(token -> token.getUsedAt() == null)
                .filter(token -> token.getExpiresAt().isAfter(LocalDateTime.now()))
                .flatMap(token -> {
                    token.setUsedAt(LocalDateTime.now());
                    tokenRepository.save(token);
                    return userRepository.findById(token.getUserId());
                });
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
