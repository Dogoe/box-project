package com.endel.demobox.model.dto;

import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class WiringSettingsDto {
    @Positive
    private double batteryToInverterMeters;

    @Positive
    private double pvToInverterMeters;

    private LocalDateTime updatedAt;
}
