package com.endel.demobox.controller.order;

import com.endel.demobox.model.dto.order.CancelRequest;
import com.endel.demobox.model.dto.order.OrderDto;
import com.endel.demobox.model.dto.order.PaymentRequest;
import com.endel.demobox.model.order.OrderStatus;
import com.endel.demobox.service.order.OrderService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderController {
    private final OrderService orderService;

    public AdminOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public List<OrderDto> list(@RequestParam(required = false) OrderStatus status) {
        return orderService.findAll(status);
    }

    @GetMapping("/{id}")
    public OrderDto get(@PathVariable Long id) {
        return orderService.getById(id);
    }

    @PostMapping("/{id}/pay")
    public OrderDto confirmPayment(@PathVariable Long id, @Valid @RequestBody PaymentRequest request, Authentication auth) {
        return orderService.confirmPayment(id, request.getAmountMxn(), request.getNote(), auth.getName());
    }

    @PostMapping("/{id}/deliver")
    public OrderDto markDelivered(@PathVariable Long id, Authentication auth) {
        return orderService.markDelivered(id, auth.getName());
    }

    @PostMapping("/{id}/pay-and-deliver")
    public OrderDto confirmPaymentAndDelivery(@PathVariable Long id, @Valid @RequestBody PaymentRequest request, Authentication auth) {
        return orderService.confirmPaymentAndDelivery(id, request.getAmountMxn(), request.getNote(), auth.getName());
    }

    @PostMapping("/{id}/cancel")
    public OrderDto cancel(@PathVariable Long id, @Valid @RequestBody CancelRequest request, Authentication auth) {
        return orderService.cancel(id, request.getReason(), auth.getName());
    }
}
