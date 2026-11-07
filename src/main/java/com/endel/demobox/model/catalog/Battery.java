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
import java.util.List;

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

    /**
     * Stud size of its battery terminals (M8 unless set). Nullable so rows saved before this column existed
     * still load; {@link #getTerminalSize()} reads them as M8.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "terminal_size", length = 10)
    private TerminalSize terminalSize = TerminalSize.M8;

    public TerminalSize getTerminalSize() {
        return TerminalSize.orDefault(terminalSize);
    }

    @Override
    protected String codeType() {
        return "BATERIA";
    }

    /** Chemistry, voltage and capacity. */
    @Override
    protected List<String> codeSpecs() {
        String chem = chemistry == null ? null : switch (chemistry) {
            case LIFEPO4 -> "LFP";
            case LITHIUM_ION -> "LION";
            case LEAD_ACID -> "PLOMO";
            case GEL -> "GEL";
            case AGM -> "AGM";
        };
        return java.util.Arrays.asList(chem, ProductCode.num(voltage) + "V", capacityAh == null ? null : ProductCode.num(capacityAh) + "Ah");
    }

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
