package com.endel.demobox.controller.order;

import com.endel.demobox.model.dto.order.CreateOrderRequest;
import com.endel.demobox.model.dto.order.CreateOrderResponse;
import com.endel.demobox.service.order.OrderService;
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

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateOrderResponse create(@Valid @RequestBody CreateOrderRequest request) {
        return new CreateOrderResponse(orderService.create(request).getFolio());
    }
}
