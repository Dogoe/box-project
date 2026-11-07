package com.endel.demobox.model.catalog;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.Set;
import java.util.TreeSet;
import java.util.List;

/**
 * Stand-alone solar charge controller (MPPT), for kits whose inverter has no built-in solar
 * charger. It sits between the panels and the battery bank; the inverter only discharges the bank.
 */
@Entity
@Table(name = "charge_controllers")
@DiscriminatorValue("CHARGE_CONTROLLER")
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class ChargeController extends Product {

    /** Battery bank voltages it can charge (most detect 12/24/48 V automatically). */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "charge_controller_battery_voltages", joinColumns = @JoinColumn(name = "controller_id"))
    @Column(name = "voltage", nullable = false)
    private Set<Integer> supportedBatteryVoltages = new TreeSet<>();

    /** Max charge current it delivers to the battery. */
    @Positive
    @Column(nullable = false)
    private double maxChargeCurrentA;

    /** Max PV open-circuit voltage the controller accepts (e.g. the "150" in a 150/60). */
    @Positive
    @Column(nullable = false)
    private double maxInputVoltage;

    /** Lowest PV voltage at which it starts charging; usually just above the battery voltage. */
    @Positive
    private Double mpptMinVoltage;

    /** Max PV input current per MPPT input. */
    @Positive
    private Double mpptMaxInputCurrentA;

    /** Manufacturer's max PV array power; when empty it's estimated from charge power like on inverters. */
    @Positive
    private Double maxPvInputPowerW;

    private Integer mpptChannels;

    @Override
    protected String codeType() {
        return "CONTROLADOR";
    }

    /** Charge current, the battery voltages it supports, and the max PV voltage. */
    @Override
    protected List<String> codeSpecs() {
        return java.util.Arrays.asList(ProductCode.num(maxChargeCurrentA) + "A",
                supportedBatteryVoltages.isEmpty() ? null
                        : supportedBatteryVoltages.stream().sorted().map(String::valueOf).collect(java.util.stream.Collectors.joining("/")) + "V",
                "PV" + ProductCode.num(maxInputVoltage) + "V");
    }

    public boolean supportsBatteryVoltage(double volts) {
        return supportedBatteryVoltages.contains((int) Math.round(volts));
    }

    /** Charge power on a given battery bus; the current limit is the same at every voltage. */
    public double maxChargePowerW(double busVolts) {
        return busVolts * maxChargeCurrentA;
    }

    public boolean isMaxPvPowerEstimated() {
        return maxPvInputPowerW == null;
    }

    public double effectiveMaxPvPowerW(double busVolts) {
        return maxPvInputPowerW != null ? maxPvInputPowerW : maxChargePowerW(busVolts) * Inverter.ESTIMATED_PV_TO_CHARGE_POWER_RATIO;
    }
}
