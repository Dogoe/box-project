package com.endel.demobox.model.dto.catalog.compatibility;

import lombok.Data;

/** How much a kit can grow with more of its own panel/battery model; the inverter is fixed. */
@Data
public class KitExpansionDto {
    private ExpansionLevel level;
    private String label;
    /** Null when the kit has no panels (or the PV limit is unknown). */
    private ExpansionAspectDto panels;
    /** Null when the kit has no batteries (or the inverter's charge power is unknown). */
    private ExpansionAspectDto batteries;
    private double inverterKw;
}
