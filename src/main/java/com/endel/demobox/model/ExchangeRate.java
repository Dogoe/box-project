package com.endel.demobox.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Single-row setting: how many MXN equal 1 USD. Used to show storefront prices
 * (stored in MXN) converted to USD on request, without touching stored prices.
 */
@Entity
@Table(name = "exchange_rates")
@Data
@NoArgsConstructor
public class ExchangeRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Positive
    @Column(nullable = false)
    private BigDecimal usdToMxnRate;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
