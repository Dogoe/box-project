package com.endel.demobox.model.dto.catalog;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class KitCapacityDto {
    /** Usable battery energy (kWh) - what the kit can sustain overnight with no sun. */
    private double nightBudgetKwh;

    /** Daily panel generation (kWh) - what the array produces over one day. */
    private double dayBudgetKwh;

    /** max(0, dayBudgetKwh - nightBudgetKwh): panel generation beyond what's needed to recharge the battery. */
    private double daySurplusKwh;

    private double estimatedMaxContinuousKw;

    /** ~24h/day appliances (fridges, etc.) that the battery alone can sustain through the night. */
    private List<String> alwaysOnAppliances = new ArrayList<>();

    /** Daytime-only appliances that fit within the panels' surplus over the battery's nightly needs. */
    private List<String> extraDaytimeAppliances = new ArrayList<>();
}
