package com.ecommerce.payment.controller;

import com.ecommerce.payment.dto.ConfirmRequest;
import com.ecommerce.payment.dto.InitiateRequest;
import com.ecommerce.payment.dto.InitiateResponse;
import com.ecommerce.payment.dto.PaymentConfigResponse;
import com.ecommerce.payment.dto.PaymentResponse;
import com.ecommerce.payment.service.PaymentService;
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

/**
 * Customer endpoints. Every call acts on the logged-in user's own payments (principal = user id from the JWT).
 * Paying is two calls: initiate (get checkout details), then confirm (send the provider's proof).
 */
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /** Which payment options are available, so the UI can show or hide them. */
    @GetMapping("/config")
    public PaymentConfigResponse config() {
        return paymentService.config();
    }

    @PostMapping("/initiate")
    @ResponseStatus(HttpStatus.CREATED)
    public InitiateResponse initiate(@AuthenticationPrincipal String userId,
                                     @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                     @Valid @RequestBody InitiateRequest request) {
        return paymentService.initiate(Long.valueOf(userId), authorization, request);
    }

    /** Returns the payment record. Check "status": SUCCESS or FAILED (a decline is not an HTTP error). */
    @PostMapping("/{id}/confirm")
    public PaymentResponse confirm(@AuthenticationPrincipal String userId, @PathVariable Long id,
                                   @Valid @RequestBody ConfirmRequest request) {
        return paymentService.confirm(Long.valueOf(userId), id, request);
    }

    @GetMapping
    public List<PaymentResponse> myPayments(@AuthenticationPrincipal String userId) {
        return paymentService.listMine(Long.valueOf(userId));
    }

    @GetMapping("/{id}")
    public PaymentResponse myPayment(@AuthenticationPrincipal String userId, @PathVariable Long id) {
        return paymentService.getMine(Long.valueOf(userId), id);
    }
}
