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

    /** Appliances that fit the night (battery) budget, each using its own default hours/day. */
    private List<String> nightAppliances = new ArrayList<>();

    /** Appliances that fit the day surplus (panels beyond the battery's nightly needs), each using its own default hours/day. */
    private List<String> dayAppliances = new ArrayList<>();
}
