package com.endel.demobox.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Single-row setting: the markup percentage applied on top of a kit's computed
 * cost (sum of its items' prices) to get the customer-facing sell price, for
 * any kit that doesn't have a manual priceOverride.
 */
@Entity
@Table(name = "profit_margins")
@Data
@NoArgsConstructor
public class ProfitMargin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @PositiveOrZero
    @Column(nullable = false)
    private BigDecimal marginPercent;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
