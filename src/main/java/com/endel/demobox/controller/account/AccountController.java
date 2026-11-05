package com.endel.demobox.controller.account;

import com.endel.demobox.exception.InvalidCredentialsException;
import com.endel.demobox.model.User;
import com.endel.demobox.model.dto.account.AccountDto;
import com.endel.demobox.model.dto.account.AuthResponse;
import com.endel.demobox.model.dto.account.EmailRequest;
import com.endel.demobox.model.dto.account.LoginRequest;
import com.endel.demobox.model.dto.account.RegisterAccountRequest;
import com.endel.demobox.model.dto.account.ResetPasswordRequest;
import com.endel.demobox.model.dto.account.UpdateAccountRequest;
import com.endel.demobox.model.dto.order.OrderDto;
import com.endel.demobox.ratelimit.LoginAttemptGuard;
import com.endel.demobox.ratelimit.RateLimit;
import com.endel.demobox.ratelimit.RateLimiter;
import com.endel.demobox.service.account.AccountService;
import com.endel.demobox.service.order.OrderService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Shop customer accounts. Sign-up/login/password recovery are public; everything else needs a customer token. */
@RestController
@RequestMapping("/api/account")
public class AccountController {
    private final AccountService accountService;
    private final OrderService orderService;
    private final RateLimiter rateLimiter;
    private final LoginAttemptGuard loginGuard;

    public AccountController(AccountService accountService, OrderService orderService,
                             RateLimiter rateLimiter, LoginAttemptGuard loginGuard) {
        this.accountService = accountService;
        this.orderService = orderService;
        this.rateLimiter = rateLimiter;
        this.loginGuard = loginGuard;
    }

    /** Lets checkout offer "log in" vs "create an account" as soon as the email is typed. */
    @PostMapping("/email-status")
    public Map<String, Boolean> emailStatus(@Valid @RequestBody EmailRequest request, HttpServletRequest http) {
        rateLimiter.consume(RateLimit.EMAIL_STATUS_PER_IP, http.getRemoteAddr());
        return Map.of("registered", accountService.isRegistered(request.getEmail()));
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterAccountRequest request, HttpServletRequest http) {
        rateLimiter.consume(RateLimit.REGISTER_PER_IP, http.getRemoteAddr());
        return accountService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return loginGuard.attempt(request.getEmail(), http.getRemoteAddr(),
                () -> accountService.login(request.getEmail(), request.getPassword()));
    }

    @GetMapping("/me")
    public AccountDto me(Authentication auth) {
        return accountService.toDto(currentUser(auth));
    }

    @PutMapping("/me")
    public AccountDto update(@Valid @RequestBody UpdateAccountRequest request, Authentication auth) {
        return accountService.update(currentUser(auth), request);
    }

    @GetMapping("/orders")
    public List<OrderDto> orders(Authentication auth) {
        return orderService.findByCustomer(currentUser(auth).getId());
    }

    @PostMapping("/verification-email")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resendVerification(Authentication auth) {
        User user = currentUser(auth);
        rateLimiter.consume(RateLimit.EMAIL_LINK_PER_EMAIL, user.getEmail());
        accountService.resendVerification(user);
    }

    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void forgotPassword(@Valid @RequestBody EmailRequest request, HttpServletRequest http) {
        // Counted for unknown emails too, so the answer never tells whether an account exists.
        rateLimiter.consume(RateLimit.EMAIL_LINK_PER_IP, http.getRemoteAddr());
        rateLimiter.consume(RateLimit.EMAIL_LINK_PER_EMAIL, request.getEmail());
        accountService.forgotPassword(request.getEmail());
    }

    @PostMapping("/reset-password")
    public AuthResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request, HttpServletRequest http) {
        rateLimiter.consume(RateLimit.RESET_PASSWORD_PER_IP, http.getRemoteAddr());
        return accountService.resetPassword(request.getToken(), request.getPassword());
    }

    private User currentUser(Authentication auth) {
        return accountService.findByEmail(auth.getName())
                .orElseThrow(() -> new InvalidCredentialsException("Tu sesión ya no es válida; inicia sesión de nuevo."));
    }
}
