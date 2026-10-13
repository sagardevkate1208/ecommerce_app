package com.ecommerce.order.entity;

import java.util.Set;

public enum OrderStatus {
    PENDING,
    PAID,
    SHIPPED,
    DELIVERED,
    CANCELLED;

    /** Allowed next states. DELIVERED and CANCELLED are final. */
    public Set<OrderStatus> allowedNext() {
        return switch (this) {
            case PENDING -> Set.of(PAID, CANCELLED);
            case PAID -> Set.of(SHIPPED, CANCELLED);
            case SHIPPED -> Set.of(DELIVERED);
            case DELIVERED, CANCELLED -> Set.of();
        };
    }

    public boolean canMoveTo(OrderStatus next) {
        return allowedNext().contains(next);
    }
}
