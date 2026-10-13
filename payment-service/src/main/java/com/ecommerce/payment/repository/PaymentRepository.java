package com.ecommerce.payment.repository;

import com.ecommerce.payment.entity.Payment;
import com.ecommerce.payment.entity.PaymentStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    Optional<Payment> findByIdAndUserId(Long id, Long userId);

    Optional<Payment> findByGatewayOrderId(String gatewayOrderId);

    boolean existsByOrderIdAndStatus(Long orderId, PaymentStatus status);

    Page<Payment> findByStatus(PaymentStatus status, Pageable pageable);

    Page<Payment> findByOrderId(Long orderId, Pageable pageable);

    /**
     * Atomically moves a payment to SUCCESS, but only if it is still CREATED or FAILED. Returns 1 for the caller
     * that won, 0 for any other caller. The browser confirmation and Razorpay's webhook can arrive together; this
     * guarantees exactly one of them goes on to mark the order paid.
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Payment p set p.status = com.ecommerce.payment.entity.PaymentStatus.SUCCESS, "
            + "p.transactionRef = :ref, p.method = :method, p.maskedReference = :masked, p.failureReason = null, "
            + "p.updatedAt = :now where p.id = :id and p.status in "
            + "(com.ecommerce.payment.entity.PaymentStatus.CREATED, com.ecommerce.payment.entity.PaymentStatus.FAILED)")
    int claimSuccess(@Param("id") Long id, @Param("ref") String ref, @Param("method") String method,
                     @Param("masked") String masked, @Param("now") Instant now);

    /** Records a failed attempt, but never overwrites a payment that already succeeded or was refunded. */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Payment p set p.status = com.ecommerce.payment.entity.PaymentStatus.FAILED, "
            + "p.failureReason = :reason, p.updatedAt = :now where p.id = :id and p.status in "
            + "(com.ecommerce.payment.entity.PaymentStatus.CREATED, com.ecommerce.payment.entity.PaymentStatus.FAILED)")
    int markFailed(@Param("id") Long id, @Param("reason") String reason, @Param("now") Instant now);

    /** SUCCESS to REFUNDED, only once. */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Payment p set p.status = com.ecommerce.payment.entity.PaymentStatus.REFUNDED, p.updatedAt = :now "
            + "where p.id = :id and p.status = com.ecommerce.payment.entity.PaymentStatus.SUCCESS")
    int markRefunded(@Param("id") Long id, @Param("now") Instant now);
}
