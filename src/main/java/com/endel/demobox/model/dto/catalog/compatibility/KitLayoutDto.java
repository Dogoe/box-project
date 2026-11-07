package com.endel.demobox.model.dto.catalog.compatibility;

import com.endel.demobox.model.catalog.Battery;
import com.endel.demobox.model.catalog.ChargeController;
import com.endel.demobox.model.catalog.Inverter;
import com.endel.demobox.model.catalog.SolarPanel;
import lombok.Data;

/**
 * How the kit's equipment ends up wired, as the review worked it out: the products, how the
 * batteries and panels are combined, and each cable run. Used to draw the wiring diagram; not sent
 * to the browser. Fields are null (or 0) for parts the kit doesn't have.
 */
@Data
public class KitLayoutDto {
    private Inverter inverter;
    private double busVolts;
    /** True when the inverter's own MPPT charges the batteries (no separate controller). */
    private boolean builtInCharger;
    private ChargeController controller;

    private Battery battery;
    private int batteryCount;
    private int batterySeries;
    private int batteryParallel;

    private SolarPanel panel;
    private int panelCount;
    private int pvSeries;
    private int pvStrings;
    private int pvInputs;
    private int pvStringsPerInput;
    private double pvColdStringVoc;

    private WiringSegmentDto batteryRun;
    private WiringSegmentDto controllerRun;
    private WiringSegmentDto pvRun;
}
