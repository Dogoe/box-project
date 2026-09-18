package com.endel.demobox.quote.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

@Data
public class ApplianceLoadDto {

    /**
     * Optional reference to a preset from GET /api/quotes/appliance-presets. When set,
     * watts/motorLoad/surgeMultiplier/runsSimultaneously are resolved from the template
     * and any of those fields set here are ignored.
     */
    private String templateId;

    /** Only used together with templateId: overrides the template's default hours/day. */
    @DecimalMax("24.0")
    private Double hoursPerDayOverride;

    private String name;

    private double watts;

    @PositiveOrZero
    @DecimalMax("24.0")
    private double hoursPerDay;

    @Positive
    private int quantity = 1;

    private boolean runsSimultaneously = true;

    /** Compressor/motor-driven loads (fridges, pumps, AC units) draw a start-up surge above their running watts. */
    private boolean motorLoad = false;

    @Positive
    private double surgeMultiplier = 3.0;

    @AssertTrue(message = "Provide either templateId, or name/watts for a custom appliance")
    public boolean isRequestValid() {
        if (templateId != null && !templateId.isBlank()) {
            return true;
        }
        return name != null && !name.isBlank() && watts > 0;
    }
}
