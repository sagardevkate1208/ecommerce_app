package com.ecommerce.cart.controller;

import com.ecommerce.cart.dto.AddItemRequest;
import com.ecommerce.cart.dto.CartResponse;
import com.ecommerce.cart.dto.UpdateItemRequest;
import com.ecommerce.cart.service.CartService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** All endpoints act on the logged-in user's own cart (principal = user id from the JWT). */
@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public CartResponse get(@AuthenticationPrincipal String userId) {
        return cartService.getCart(Long.valueOf(userId));
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    public CartResponse addItem(@AuthenticationPrincipal String userId, @Valid @RequestBody AddItemRequest request) {
        return cartService.addItem(Long.valueOf(userId), request);
    }

    @PutMapping("/items/{productId}")
    public CartResponse updateItem(@AuthenticationPrincipal String userId, @PathVariable Long productId,
                                   @Valid @RequestBody UpdateItemRequest request) {
        return cartService.updateItem(Long.valueOf(userId), productId, request);
    }

    @DeleteMapping("/items/{productId}")
    public CartResponse removeItem(@AuthenticationPrincipal String userId, @PathVariable Long productId) {
        return cartService.removeItem(Long.valueOf(userId), productId);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear(@AuthenticationPrincipal String userId) {
        cartService.clear(Long.valueOf(userId));
    }
}
