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

    /**
     * Manufacturer's max PV array power. Optional: often higher than charge power because surplus
     * feeds loads directly. When empty, the over-paneling check estimates from charge power instead.
     */
    @Positive
    private Double maxPvInputPowerW;

    /** Max PV input current per MPPT channel. */
    @Positive
    private Double mpptMaxInputCurrentA;

    // Datasheet PV limits typically run 1.25-1.5x charge power, since surplus feeds loads directly.
    public static final double ESTIMATED_PV_TO_CHARGE_POWER_RATIO = 1.30;

    public double getMaxChargePowerW() {
        return batteryVoltage == null || mpptMaxChargeCurrentA == null ? 0 : batteryVoltage * mpptMaxChargeCurrentA;
    }

    public boolean isMaxPvPowerEstimated() {
        return maxPvInputPowerW == null;
    }

    /** Datasheet max PV power when captured, otherwise estimated from charge power; 0 when neither is known. */
    public double getEffectiveMaxPvPowerW() {
        return maxPvInputPowerW != null ? maxPvInputPowerW : getMaxChargePowerW() * ESTIMATED_PV_TO_CHARGE_POWER_RATIO;
    }
}
