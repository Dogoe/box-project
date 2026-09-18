package com.endel.demobox.model.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "batteries")
@DiscriminatorValue("BATTERY")
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class Battery extends Product {

    @Positive
    @Column(nullable = false)
    private double capacityKwh;

    @Positive
    @Column(nullable = false)
    private double voltage;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BatteryChemistry chemistry;

    private double depthOfDischargePercent;

    private Integer cycleLife;
}
