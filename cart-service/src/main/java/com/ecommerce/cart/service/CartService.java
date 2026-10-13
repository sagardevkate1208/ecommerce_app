package com.ecommerce.cart.service;

import com.ecommerce.cart.client.CatalogClient;
import com.ecommerce.cart.dto.AddItemRequest;
import com.ecommerce.cart.dto.CartItemResponse;
import com.ecommerce.cart.dto.CartResponse;
import com.ecommerce.cart.dto.ProductInfo;
import com.ecommerce.cart.dto.UpdateItemRequest;
import com.ecommerce.cart.entity.Cart;
import com.ecommerce.cart.entity.CartItem;
import com.ecommerce.cart.exception.ApiException;
import com.ecommerce.cart.repository.CartRepository;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CartService {

    private static final int MAX_QUANTITY_PER_ITEM = 99;

    private final CartRepository carts;
    private final CatalogClient catalog;

    public CartService(CartRepository carts, CatalogClient catalog) {
        this.carts = carts;
        this.catalog = catalog;
    }

    @Transactional(readOnly = true)
    public CartResponse getCart(Long userId) {
        return carts.findByUserId(userId).map(this::toResponse).orElseGet(this::emptyCart);
    }

    public CartResponse addItem(Long userId, AddItemRequest req) {
        // Validate against Catalog before touching the cart (no DB transaction work is wasted on a bad product).
        ProductInfo product = catalog.getProduct(req.productId());
        if (!product.active()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Product not found");
        }

        Cart cart = carts.findByUserId(userId).orElseGet(() -> new Cart(userId));
        CartItem item = findItem(cart, req.productId());
        if (item == null) {
            item = new CartItem();
            item.setCart(cart);
            item.setProductId(product.id());
            item.setQuantity(req.quantity());
            cart.getItems().add(item);
        } else {
            int newQuantity = item.getQuantity() + req.quantity();
            if (newQuantity > MAX_QUANTITY_PER_ITEM) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "Maximum quantity per item is " + MAX_QUANTITY_PER_ITEM);
            }
            item.setQuantity(newQuantity);
        }
        // Refresh the snapshot so the cart shows what Catalog says right now.
        item.setProductName(product.name());
        item.setSku(product.sku());
        item.setImageUrl(product.imageUrl());
        item.setUnitPrice(product.price());

        cart.touch();
        return toResponse(carts.save(cart));
    }

    public CartResponse updateItem(Long userId, Long productId, UpdateItemRequest req) {
        Cart cart = loadCart(userId);
        CartItem item = findItem(cart, productId);
        if (item == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Item not in cart");
        }
        item.setQuantity(req.quantity());
        cart.touch();
        return toResponse(cart);
    }

    public CartResponse removeItem(Long userId, Long productId) {
        Cart cart = loadCart(userId);
        CartItem item = findItem(cart, productId);
        if (item == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Item not in cart");
        }
        cart.getItems().remove(item);
        cart.touch();
        return toResponse(cart);
    }

    public void clear(Long userId) {
        carts.findByUserId(userId).ifPresent(cart -> {
            cart.getItems().clear();
            cart.touch();
        });
    }

    private Cart loadCart(Long userId) {
        return carts.findByUserId(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Item not in cart"));
    }

    private CartItem findItem(Cart cart, Long productId) {
        return cart.getItems().stream()
                .filter(i -> i.getProductId().equals(productId))
                .findFirst()
                .orElse(null);
    }

    private CartResponse emptyCart() {
        return new CartResponse(List.of(), 0, BigDecimal.ZERO.setScale(2));
    }

    private CartResponse toResponse(Cart cart) {
        List<CartItemResponse> items = cart.getItems().stream()
                .map(i -> new CartItemResponse(i.getProductId(), i.getProductName(), i.getSku(), i.getImageUrl(),
                        i.getUnitPrice(), i.getQuantity(),
                        i.getUnitPrice().multiply(BigDecimal.valueOf(i.getQuantity()))))
                .toList();
        int totalItems = items.stream().mapToInt(CartItemResponse::quantity).sum();
        BigDecimal subtotal = items.stream().map(CartItemResponse::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2);
        return new CartResponse(items, totalItems, subtotal);
    }
}
