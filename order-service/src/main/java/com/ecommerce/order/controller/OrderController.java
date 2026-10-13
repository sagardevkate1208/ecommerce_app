package com.ecommerce.order.controller;

import com.ecommerce.order.dto.CheckoutRequest;
import com.ecommerce.order.dto.OrderResponse;
import com.ecommerce.order.service.OrderService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Customer endpoints. Every call acts on the logged-in user's own orders (principal = user id from the JWT). */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse checkout(@AuthenticationPrincipal String userId,
                                  @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                  @Valid @RequestBody CheckoutRequest request) {
        return orderService.placeOrder(Long.valueOf(userId), authorization, request);
    }

    @GetMapping
    public List<OrderResponse> myOrders(@AuthenticationPrincipal String userId) {
        return orderService.listMine(Long.valueOf(userId));
    }

    @GetMapping("/{id}")
    public OrderResponse myOrder(@AuthenticationPrincipal String userId, @PathVariable Long id) {
        return orderService.getMine(Long.valueOf(userId), id);
    }

    @PostMapping("/{id}/cancel")
    public OrderResponse cancel(@AuthenticationPrincipal String userId, @PathVariable Long id) {
        return orderService.cancelMine(Long.valueOf(userId), id);
    }
}
