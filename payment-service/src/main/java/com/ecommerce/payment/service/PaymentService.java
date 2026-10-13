package com.ecommerce.payment.service;

import com.ecommerce.payment.client.OrderClient;
import com.ecommerce.payment.dto.ConfirmRequest;
import com.ecommerce.payment.dto.InitiateRequest;
import com.ecommerce.payment.dto.InitiateResponse;
import com.ecommerce.payment.dto.OrderInfo;
import com.ecommerce.payment.dto.PageResponse;
import com.ecommerce.payment.dto.PaymentConfigResponse;
import com.ecommerce.payment.dto.PaymentResponse;
import com.ecommerce.payment.entity.Payment;
import com.ecommerce.payment.entity.PaymentStatus;
import com.ecommerce.payment.exception.ApiException;
import com.ecommerce.payment.gateway.PaymentGateway;
import com.ecommerce.payment.gateway.RazorpayPaymentGateway;
import com.ecommerce.payment.repository.PaymentRepository;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Not @Transactional on purpose: these methods call other services and the payment provider over HTTP, and each
 * repository call is its own short transaction, so no database connection is held while waiting on the network.
 * <p>
 * Two gateways are wired in: the @Primary one (Razorpay) is injected by plain type, and the one qualified "mock"
 * is injected by name. The mock only exists in dev and test, so it is requested through an ObjectProvider.
 */
@Service
public class PaymentService {

    public static final String RAZORPAY = "RAZORPAY";
    public static final String MOCK = "MOCK";
    private static final String CURRENCY = "INR";
    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private static final int MAX_PAGE_SIZE = 100;

    private final PaymentRepository payments;
    private final OrderClient orderClient;
    private final PaymentGateway primaryGateway;
    private final ObjectProvider<PaymentGateway> mockGateway;

    public PaymentService(PaymentRepository payments, OrderClient orderClient, PaymentGateway primaryGateway,
                          @Qualifier("mock") ObjectProvider<PaymentGateway> mockGateway) {
        this.payments = payments;
        this.orderClient = orderClient;
        this.primaryGateway = primaryGateway;
        this.mockGateway = mockGateway;
    }

    public PaymentConfigResponse config() {
        boolean razorpayOn = razorpayEnabled();
        String keyId = primaryGateway instanceof RazorpayPaymentGateway r && r.isAvailable() ? r.keyId() : null;
        return new PaymentConfigResponse(defaultProvider(), razorpayOn && primaryGateway.isAvailable(), keyId,
                mockGateway.getIfAvailable() != null);
    }

    /** True only when the Razorpay bean exists (razorpay.enabled=true). Otherwise the mock is the only gateway. */
    private boolean razorpayEnabled() {
        return primaryGateway instanceof RazorpayPaymentGateway;
    }

    /** What is used when the request names no provider: Razorpay if it is switched on, otherwise the mock. */
    private String defaultProvider() {
        return razorpayEnabled() ? RAZORPAY : MOCK;
    }

    /** Step 1: register the amount with the provider and give the browser what it needs to open checkout. */
    public InitiateResponse initiate(Long userId, String authorizationHeader, InitiateRequest req) {
        // Order verifies the order belongs to this user (404 otherwise).
        OrderInfo order = orderClient.getOrder(authorizationHeader, req.orderId());
        if (!"PENDING".equals(order.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "Order is not payable (status is " + order.status() + ")");
        }
        if (payments.existsByOrderIdAndStatus(order.id(), PaymentStatus.SUCCESS)) {
            throw new ApiException(HttpStatus.CONFLICT, "Order is already paid");
        }
        String provider = req.provider() == null || req.provider().isBlank()
                ? defaultProvider() : req.provider().trim().toUpperCase();
        PaymentGateway gateway = gatewayFor(provider);

        PaymentGateway.GatewayOrder gatewayOrder =
                gateway.createOrder(order.totalAmount(), CURRENCY, "order_" + order.id());

        Payment payment = new Payment();
        payment.setOrderId(order.id());
        payment.setUserId(userId);
        payment.setAmount(order.totalAmount());
        payment.setProvider(provider);
        payment.setGatewayOrderId(gatewayOrder.gatewayOrderId());
        payment.setMethod(provider); // refined once the customer has actually paid
        payment.setStatus(PaymentStatus.CREATED);
        payment = payments.save(payment);

        return new InitiateResponse(payment.getId(), provider, payment.getGatewayOrderId(), payment.getAmount(),
                CURRENCY, gatewayOrder.keyId());
    }

    /** Step 3: check the provider's proof of payment and, if it is genuine, mark the order paid. */
    public PaymentResponse confirm(Long userId, Long paymentId, ConfirmRequest req) {
        // Another user's payment looks exactly like a missing one.
        Payment payment = payments.findByIdAndUserId(paymentId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Payment not found"));
        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            return toResponse(payment); // already confirmed (for example by the webhook): nothing more to do
        }
        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            throw new ApiException(HttpStatus.CONFLICT, "This payment was refunded");
        }

        PaymentGateway.ChargeResult result =
                gatewayFor(providerOf(payment)).confirm(payment.getGatewayOrderId(), req.details());
        if (result.approved()) {
            return toResponse(completeSuccess(payment.getId(), result));
        }
        payments.markFailed(payment.getId(), result.failureReason(), Instant.now());
        return toResponse(reload(payment.getId()));
    }

    /** Called by the Razorpay webhook when a payment was captured. Safe to call repeatedly. */
    public void recordGatewayCapture(String gatewayOrderId, String gatewayPaymentId, String method) {
        payments.findByGatewayOrderId(gatewayOrderId).ifPresentOrElse(
                payment -> completeSuccess(payment.getId(), new PaymentGateway.ChargeResult(
                        true, gatewayPaymentId, method == null ? RAZORPAY : method.toUpperCase(),
                        gatewayPaymentId, null)),
                () -> log.warn("Webhook for unknown gateway order {}", gatewayOrderId));
    }

    /** Called by the Razorpay webhook when a payment attempt failed. A payment that already succeeded is untouched. */
    public void recordGatewayFailure(String gatewayOrderId, String reason) {
        payments.findByGatewayOrderId(gatewayOrderId)
                .ifPresent(payment -> payments.markFailed(payment.getId(), reason, Instant.now()));
    }

    public List<PaymentResponse> listMine(Long userId) {
        return payments.findByUserIdOrderByCreatedAtDescIdDesc(userId).stream().map(this::toResponse).toList();
    }

    public PaymentResponse getMine(Long userId, Long paymentId) {
        return toResponse(payments.findByIdAndUserId(paymentId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Payment not found")));
    }

    // ---- admin ----

    /** Lists payments. An orderId filter wins over the status filter (an order has only a few payments). */
    public PageResponse<PaymentResponse> adminList(PaymentStatus status, Long orderId, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        Page<Payment> result;
        if (orderId != null) {
            result = payments.findByOrderId(orderId, pageable);
        } else if (status != null) {
            result = payments.findByStatus(status, pageable);
        } else {
            result = payments.findAll(pageable);
        }
        return new PageResponse<>(result.getContent().stream().map(this::toResponse).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    /**
     * Refund. The order is checked first (a shipped order cannot be refunded), then the money goes back, and only
     * then is the order cancelled. With a real provider this order matters: money must never be returned for an order
     * that is not going to be cancelled, and a refund must not be attempted when it is already known to be refused.
     */
    public PaymentResponse refund(Long paymentId, String adminAuthorizationHeader) {
        Payment payment = load(paymentId);
        if (payment.getStatus() != PaymentStatus.SUCCESS) {
            throw new ApiException(HttpStatus.CONFLICT, "Only a successful payment can be refunded (status is "
                    + payment.getStatus() + ")");
        }
        OrderInfo order = orderClient.getOrderAsAdmin(adminAuthorizationHeader, payment.getOrderId());
        if (!"PAID".equals(order.status()) && !"PENDING".equals(order.status())) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Order is " + order.status() + " and can no longer be cancelled, so it cannot be refunded");
        }

        PaymentGateway.RefundResult result =
                gatewayFor(providerOf(payment)).refund(payment.getTransactionRef(), payment.getAmount());
        if (!result.approved()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, result.failureReason() == null
                    ? "Refund rejected by the payment provider" : result.failureReason());
        }
        payments.markRefunded(payment.getId(), Instant.now());

        try {
            orderClient.updateStatus(payment.getOrderId(), "CANCELLED");
        } catch (ApiException e) {
            // The money is already back with the customer; flag the order so someone cancels it by hand.
            log.error("Payment {} refunded but order {} could not be cancelled: {}",
                    paymentId, payment.getOrderId(), e.getMessage());
            Payment refunded = reload(paymentId);
            refunded.setOrderSynced(false);
            refunded.touch();
            payments.save(refunded);
        }
        return toResponse(reload(paymentId));
    }

    /** Retry telling Order about a successful payment when the first attempt failed. */
    public PaymentResponse syncOrder(Long paymentId) {
        Payment payment = load(paymentId);
        if (payment.getStatus() != PaymentStatus.SUCCESS) {
            throw new ApiException(HttpStatus.CONFLICT, "Only a successful payment needs syncing");
        }
        if (!payment.isOrderSynced()) {
            orderClient.updateStatus(payment.getOrderId(), "PAID");
            payment.setOrderSynced(true);
            payment.touch();
            payment = payments.save(payment);
        }
        return toResponse(payment);
    }

    // ---- helpers ----

    /**
     * Moves the payment to SUCCESS exactly once (the conditional update decides the winner when the browser and the
     * webhook arrive together), and only the winner tells the order service.
     */
    private Payment completeSuccess(Long paymentId, PaymentGateway.ChargeResult result) {
        int claimed = payments.claimSuccess(paymentId, result.gatewayPaymentId(), result.method(),
                result.maskedReference(), Instant.now());
        Payment payment = reload(paymentId);
        return claimed == 1 ? tryMarkOrderPaid(payment) : payment;
    }

    private Payment tryMarkOrderPaid(Payment payment) {
        try {
            orderClient.updateStatus(payment.getOrderId(), "PAID");
            payment.setOrderSynced(true);
        } catch (ApiException e) {
            // The charge happened. Keep the payment, flag it, and let an admin retry via syncOrder.
            log.error("Payment {} succeeded but order {} could not be marked PAID: {}",
                    payment.getId(), payment.getOrderId(), e.getMessage());
            payment.setOrderSynced(false);
        }
        payment.touch();
        return payments.save(payment);
    }

    private PaymentGateway gatewayFor(String provider) {
        return switch (provider) {
            case RAZORPAY -> {
                // Never hand a "RAZORPAY" request to the mock that happens to be the only gateway.
                if (!razorpayEnabled()) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "Razorpay is not enabled in this environment");
                }
                yield primaryGateway;
            }
            case MOCK -> {
                PaymentGateway mock = mockGateway.getIfAvailable();
                if (mock == null) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "Mock payments are not enabled in this environment");
                }
                yield mock;
            }
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "Unknown payment provider: " + provider);
        };
    }

    /** Rows from before gateways existed have no provider; they were all mock payments. */
    private String providerOf(Payment payment) {
        return payment.getProvider() == null ? MOCK : payment.getProvider();
    }

    private Payment load(Long paymentId) {
        return payments.findById(paymentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Payment not found"));
    }

    private Payment reload(Long paymentId) {
        return payments.findById(paymentId).orElseThrow();
    }

    private PaymentResponse toResponse(Payment p) {
        return new PaymentResponse(p.getId(), p.getOrderId(), p.getUserId(), p.getAmount(), providerOf(p),
                p.getGatewayOrderId(), p.getMethod(), p.getMaskedReference(), p.getStatus(), p.getTransactionRef(),
                p.getFailureReason(), p.isOrderSynced(), p.getCreatedAt(), p.getUpdatedAt());
    }
}
