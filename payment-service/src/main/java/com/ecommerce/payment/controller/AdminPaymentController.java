package com.ecommerce.payment.controller;

import com.ecommerce.payment.dto.PageResponse;
import com.ecommerce.payment.dto.PaymentResponse;
import com.ecommerce.payment.entity.PaymentStatus;
import com.ecommerce.payment.service.PaymentService;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Admin-only (enforced in SecurityConfig for /api/admin/**). */
@RestController
@RequestMapping("/api/admin/payments")
public class AdminPaymentController {

    private final PaymentService paymentService;

    public AdminPaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping
    public PageResponse<PaymentResponse> list(@RequestParam(required = false) PaymentStatus status,
                                              @RequestParam(required = false) Long orderId,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return paymentService.adminList(status, orderId, page, size);
    }

    @PostMapping("/{id}/refund")
    public PaymentResponse refund(@PathVariable Long id,
                                  @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        // The admin's own token is forwarded so Order can show us the order whoever placed it.
        return paymentService.refund(id, authorization);
    }

    @PostMapping("/{id}/sync-order")
    public PaymentResponse syncOrder(@PathVariable Long id) {
        return paymentService.syncOrder(id);
    }
}
