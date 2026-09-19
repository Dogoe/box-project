package com.endel.demobox.quote;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ApplianceTemplate {
    private String id;
    private ApplianceCategory category;
    private String label;
    private double wattage;
    private double defaultHoursPerDay;
    private boolean motorLoad;
    private double surgeMultiplier;
    private boolean defaultRunsSimultaneously;
}
