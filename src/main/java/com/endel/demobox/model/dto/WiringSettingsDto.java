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

    @Positive
    private double controllerToBatteryMeters = WiringSettingsDto.DEFAULT_CONTROLLER_TO_BATTERY_METERS;

    public static final double DEFAULT_CONTROLLER_TO_BATTERY_METERS = 2.0;

    private LocalDateTime updatedAt;
}
