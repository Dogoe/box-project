package com.endel.demobox.model.order;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A guest checkout paid in cash and confirmed by an admin. Prices are snapshotted at checkout so
 * later margin or cost changes never alter an existing order. ("order" is a reserved SQL word.)
 */
@Entity
@Table(name = "customer_orders")
@Data
@NoArgsConstructor
public class CustomerOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Public, hard-to-guess reference; the only key needed to view the order page. */
    @Column(nullable = false, unique = true, length = 20)
    private String folio;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status = OrderStatus.PENDING;

    @Column(nullable = false, length = 120)
    private String customerName;

    /** 10-digit Mexican number, normalized. */
    @Column(nullable = false, length = 10)
    private String customerPhone;

    @Column(length = 200)
    private String customerEmail;

    @Column(nullable = false, length = 200)
    private String street;

    @Column(nullable = false, length = 120)
    private String neighborhood;

    @Column(nullable = false, length = 5)
    private String postalCode;

    @Column(length = 500)
    private String addressReferences;

    @Column(nullable = false, length = 80)
    private String city;

    @Column(length = 1000)
    private String notes;

    @Column(nullable = false)
    private BigDecimal totalMxn;

    /** Exchange rate at checkout, for reference only; orders are always charged in MXN. */
    private BigDecimal usdToMxnRate;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("id")
    private List<OrderItem> items = new ArrayList<>();

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime paidAt;
    private BigDecimal paidAmountMxn;
    @Column(length = 500)
    private String paymentNote;
    private String paidBy;

    private LocalDateTime deliveredAt;
    private String deliveredBy;

    private LocalDateTime cancelledAt;
    private String cancelledBy;
    @Column(length = 500)
    private String cancelReason;
}
