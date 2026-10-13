package com.ecommerce.order.controller;

import com.ecommerce.order.dto.OrderResponse;
import com.ecommerce.order.dto.OrderStats;
import com.ecommerce.order.dto.PageResponse;
import com.ecommerce.order.dto.StatusUpdateRequest;
import com.ecommerce.order.entity.OrderStatus;
import com.ecommerce.order.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Admin-only (enforced in SecurityConfig for /api/admin/**). */
@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderController {

    private final OrderService orderService;

    public AdminOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public PageResponse<OrderResponse> list(@RequestParam(required = false) OrderStatus status,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return orderService.adminList(status, page, size);
    }

    @GetMapping("/stats")
    public OrderStats stats() {
        return orderService.adminStats();
    }

    @GetMapping("/{id}")
    public OrderResponse get(@PathVariable Long id) {
        return orderService.adminGet(id);
    }

    @PutMapping("/{id}/status")
    public OrderResponse updateStatus(@PathVariable Long id, @Valid @RequestBody StatusUpdateRequest request) {
        return orderService.adminUpdateStatus(id, request.status());
    }
}
