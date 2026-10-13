package com.ecommerce.order.service;

import com.ecommerce.order.client.CartClient;
import com.ecommerce.order.client.CatalogClient;
import com.ecommerce.order.dto.CartInfo;
import com.ecommerce.order.dto.CheckoutRequest;
import com.ecommerce.order.dto.OrderItemResponse;
import com.ecommerce.order.dto.OrderResponse;
import com.ecommerce.order.dto.OrderStats;
import com.ecommerce.order.dto.PageResponse;
import com.ecommerce.order.dto.ProductInfo;
import com.ecommerce.order.dto.ShippingAddress;
import com.ecommerce.order.entity.CustomerOrder;
import com.ecommerce.order.entity.OrderItem;
import com.ecommerce.order.entity.OrderStatus;
import com.ecommerce.order.exception.ApiException;
import com.ecommerce.order.repository.OrderRepository;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private static final int MAX_PAGE_SIZE = 100;

    private final OrderRepository orders;
    private final CartClient cartClient;
    private final CatalogClient catalogClient;

    public OrderService(OrderRepository orders, CartClient cartClient, CatalogClient catalogClient) {
        this.orders = orders;
        this.cartClient = cartClient;
        this.catalogClient = catalogClient;
    }

    /**
     * Checkout. Deliberately not @Transactional: it makes HTTP calls to other services, and we do not want
     * to hold a database connection open while waiting for them. The order is saved in one short transaction.
     */
    public OrderResponse placeOrder(Long userId, String authorizationHeader, CheckoutRequest req) {
        CartInfo cart = cartClient.getCart(authorizationHeader);
        if (cart == null || cart.items() == null || cart.items().isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Cart is empty");
        }

        CustomerOrder order = new CustomerOrder();
        order.setUserId(userId);
        applyAddress(order, req.shippingAddress());

        BigDecimal total = BigDecimal.ZERO;
        for (CartInfo.CartItemInfo cartItem : cart.items()) {
            // Always price from Catalog, never from what the cart remembers.
            ProductInfo product = catalogClient.getProduct(cartItem.productId());
            if (!product.active()) {
                throw new ApiException(HttpStatus.CONFLICT,
                        "Product " + product.id() + " is no longer available; remove it from your cart");
            }
            OrderItem item = new OrderItem();
            item.setOrder(order);
            item.setProductId(product.id());
            item.setProductName(product.name());
            item.setSku(product.sku());
            item.setUnitPrice(product.price());
            item.setQuantity(cartItem.quantity());
            item.setLineTotal(product.price().multiply(BigDecimal.valueOf(cartItem.quantity())));
            order.getItems().add(item);
            total = total.add(item.getLineTotal());
        }
        order.setTotalAmount(total.setScale(2));

        CustomerOrder saved = orders.save(order);

        // The order exists now; a failure to empty the cart must not undo it.
        try {
            cartClient.clear(authorizationHeader);
        } catch (ApiException e) {
            log.warn("Order {} placed but the cart could not be cleared: {}", saved.getId(), e.getMessage());
        }
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> listMine(Long userId) {
        return orders.findByUserIdOrderByCreatedAtDescIdDesc(userId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse getMine(Long userId, Long orderId) {
        return toResponse(loadMine(userId, orderId));
    }

    /** Customers may cancel only before payment. Cancelling a paid order needs a refund, so it is admin-only. */
    @Transactional
    public OrderResponse cancelMine(Long userId, Long orderId) {
        CustomerOrder order = loadMine(userId, orderId);
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, "Only a pending order can be cancelled (status is "
                    + order.getStatus() + ")");
        }
        order.setStatus(OrderStatus.CANCELLED);
        order.touch();
        return toResponse(order);
    }

    // ---- admin ----

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> adminList(OrderStatus status, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        Page<CustomerOrder> result = status == null ? orders.findAll(pageable) : orders.findByStatus(status, pageable);
        return new PageResponse<>(result.getContent().stream().map(this::toResponse).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public OrderStats adminStats() {
        Map<OrderStatus, Long> counts = new EnumMap<>(OrderStatus.class);
        for (OrderStatus s : OrderStatus.values()) {
            counts.put(s, 0L);
        }
        BigDecimal revenue = BigDecimal.ZERO;
        long total = 0;
        for (Object[] row : orders.statusTotals()) {
            OrderStatus status = (OrderStatus) row[0];
            long count = ((Number) row[1]).longValue();
            counts.put(status, count);
            total += count;
            if (status == OrderStatus.PAID || status == OrderStatus.SHIPPED || status == OrderStatus.DELIVERED) {
                revenue = revenue.add(new BigDecimal(row[2].toString()));
            }
        }
        return new OrderStats(counts, total, revenue.setScale(2));
    }

    @Transactional(readOnly = true)
    public OrderResponse adminGet(Long orderId) {
        return toResponse(orders.findById(orderId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Order not found")));
    }

    @Transactional
    public OrderResponse adminUpdateStatus(Long orderId, OrderStatus next) {
        CustomerOrder order = orders.findById(orderId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Order not found"));
        if (!order.getStatus().canMoveTo(next)) {
            throw new ApiException(HttpStatus.CONFLICT, "Cannot change status from " + order.getStatus()
                    + " to " + next + " (allowed: " + order.getStatus().allowedNext() + ")");
        }
        order.setStatus(next);
        order.touch();
        return toResponse(order);
    }

    // ---- helpers ----

    private CustomerOrder loadMine(Long userId, Long orderId) {
        // Another user's order looks exactly like a missing one.
        return orders.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Order not found"));
    }

    private void applyAddress(CustomerOrder o, ShippingAddress a) {
        o.setRecipientName(a.recipientName().trim());
        o.setPhone(a.phone());
        o.setLine1(a.line1());
        o.setLine2(a.line2());
        o.setCity(a.city());
        o.setState(a.state());
        o.setPostalCode(a.postalCode());
        o.setCountry(a.country());
    }

    private OrderResponse toResponse(CustomerOrder o) {
        ShippingAddress address = new ShippingAddress(o.getRecipientName(), o.getPhone(), o.getLine1(),
                o.getLine2(), o.getCity(), o.getState(), o.getPostalCode(), o.getCountry());
        List<OrderItemResponse> items = o.getItems().stream()
                .map(i -> new OrderItemResponse(i.getProductId(), i.getProductName(), i.getSku(), i.getUnitPrice(),
                        i.getQuantity(), i.getLineTotal()))
                .toList();
        return new OrderResponse(o.getId(), o.getUserId(), o.getStatus(), o.getTotalAmount(), address, items,
                o.getCreatedAt(), o.getUpdatedAt());
    }
}
