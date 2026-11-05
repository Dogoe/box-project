package com.endel.demobox.repository.order;

import com.endel.demobox.model.order.CustomerOrder;
import com.endel.demobox.model.order.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, Long> {
    Optional<CustomerOrder> findByFolio(String folio);

    boolean existsByFolio(String folio);

    List<CustomerOrder> findAllByOrderByCreatedAtDesc();

    List<CustomerOrder> findByStatusOrderByCreatedAtDesc(OrderStatus status);

    List<CustomerOrder> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<CustomerOrder> findByUserIdIsNullAndCustomerEmailIgnoreCase(String customerEmail);
}
