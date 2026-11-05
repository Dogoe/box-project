package com.endel.demobox.controller.order;

import com.endel.demobox.model.User;
import com.endel.demobox.model.dto.order.CreateOrderRequest;
import com.endel.demobox.model.dto.order.CreateOrderResponse;
import com.endel.demobox.ratelimit.RateLimit;
import com.endel.demobox.ratelimit.RateLimiter;
import com.endel.demobox.service.account.AccountService;
import com.endel.demobox.service.order.OrderService;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Public guest checkout. */
@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderService orderService;
    private final AccountService accountService;
    private final RateLimiter rateLimiter;

    public OrderController(OrderService orderService, AccountService accountService, RateLimiter rateLimiter) {
        this.orderService = orderService;
        this.accountService = accountService;
        this.rateLimiter = rateLimiter;
    }

    /** Guest checkout; when a customer token is sent, the order is linked to that account. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateOrderResponse create(@Valid @RequestBody CreateOrderRequest request, Authentication auth,
                                      HttpServletRequest http) {
        rateLimiter.consume(RateLimit.ORDER_PER_IP, http.getRemoteAddr());
        User customer = auth == null || auth instanceof AnonymousAuthenticationToken
                ? null
                : accountService.findByEmail(auth.getName()).orElse(null);
        return new CreateOrderResponse(orderService.create(request, customer).getFolio());
    }
}
