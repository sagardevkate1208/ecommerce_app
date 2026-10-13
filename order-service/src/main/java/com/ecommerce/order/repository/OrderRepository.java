package com.ecommerce.order.repository;

import com.ecommerce.order.entity.CustomerOrder;
import com.ecommerce.order.entity.OrderStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OrderRepository extends JpaRepository<CustomerOrder, Long> {

    List<CustomerOrder> findByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    Optional<CustomerOrder> findByIdAndUserId(Long id, Long userId);

    Page<CustomerOrder> findByStatus(OrderStatus status, Pageable pageable);

    /** One row per status that has orders: [status, number of orders, sum of totals]. */
    @Query("select o.status, count(o), coalesce(sum(o.totalAmount), 0) from CustomerOrder o group by o.status")
    List<Object[]> statusTotals();
}
