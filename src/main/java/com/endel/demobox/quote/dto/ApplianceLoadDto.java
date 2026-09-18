package com.endel.demobox.quote.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

@Data
public class ApplianceLoadDto {

    @NotBlank
    private String name;

    @Positive
    private double watts;

    @PositiveOrZero
    @DecimalMax("24.0")
    private double hoursPerDay;

    @Positive
    private int quantity = 1;

    /** Whether this load is typically running at the same time as the household's peak load. */
    private boolean runsSimultaneously = true;

    /** Compressor/motor-driven loads (fridges, pumps, AC units) draw a start-up surge above their running watts. */
    private boolean motorLoad = false;

    @Positive
    private double surgeMultiplier = 3.0;
}
