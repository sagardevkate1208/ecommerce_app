package com.ecommerce.cart.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** One cart per user. userId comes from the JWT subject issued by auth-service. */
@Entity
@Table(name = "carts")
public class Cart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long userId;

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CartItem> items = new ArrayList<>();

    public Cart() {
    }

    public Cart(Long userId) {
        this.userId = userId;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<CartItem> getItems() { return items; }
    public void touch() { this.updatedAt = Instant.now(); }
}
