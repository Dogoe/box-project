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

    // Nullable at the DB level only so ddl-auto can add it to tables that already have rows;
    // LegacySchemaMigration backfills it and the DTO requires it.
    @Positive
    private Double capacityAh;

    @Positive
    @Column(nullable = false)
    private double voltage;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BatteryChemistry chemistry = BatteryChemistry.LIFEPO4;

    private Integer cycleLife;

    /**
     * Continuous discharge current the battery's BMS allows, from its datasheet (lithium only).
     * Optional: without it a lithium battery is assumed to deliver its capacity in amps (1C).
     */
    @Positive
    @Column(name = "bms_max_discharge_a")
    private Double bmsMaxDischargeA;

    /**
     * Rating of the output breaker built into the battery (rack batteries usually have one), in amps.
     * When set, the kit doesn't need a separate main fuse and battery switch for it.
     */
    @Positive
    @Column(name = "integrated_breaker_a")
    private Double integratedBreakerA;

    public double getCapacityKwh() {
        return capacityAh == null ? 0 : capacityAh * voltage / 1000.0;
    }

    public double getDepthOfDischarge() {
        return chemistry.getDepthOfDischarge();
    }

    public double getMaxDischargeCurrentA() {
        if (chemistry.hasBms() && bmsMaxDischargeA != null) {
            return bmsMaxDischargeA;
        }
        return capacityAh == null ? 0 : capacityAh * chemistry.getMaxDischargeCRate();
    }

    /** True when the current above is the 1C assumption for a lithium battery without its BMS rating. */
    public boolean isBmsCurrentAssumed() {
        return chemistry.hasBms() && bmsMaxDischargeA == null;
    }
}
