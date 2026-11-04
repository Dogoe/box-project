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

    /** Max PV open-circuit voltage the MPPT input accepts. */
    private double maxInputVoltage;

    // New fields are nullable at the DB level so ddl-auto can add them to existing rows;
    // compatibility checks report "missing data" instead of failing when they're empty.

    /** DC battery bus voltage the inverter/charger runs on (12, 24 or 48 V). */
    @Positive
    private Double batteryVoltage;

    /** Lowest PV voltage at which the MPPT starts tracking/charging. */
    @Positive
    private Double mpptMinVoltage;

    /** Max charge current the MPPT delivers to the battery. */
    @Positive
    private Double mpptMaxChargeCurrentA;

    /** Max PV input current per MPPT channel. */
    @Positive
    private Double mpptMaxInputCurrentA;

    public double getMaxChargePowerW() {
        return batteryVoltage == null || mpptMaxChargeCurrentA == null ? 0 : batteryVoltage * mpptMaxChargeCurrentA;
    }
}
