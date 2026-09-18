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
@Table(name = "inverters")
@DiscriminatorValue("INVERTER")
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class Inverter extends Product {

    @Positive
    @Column(nullable = false)
    private double ratedPowerKw;

    @Positive
    @Column(nullable = false)
    private double surgePowerKw;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InverterType inverterType;

    private Integer mpptChannels;

    private double maxInputVoltage;
}
