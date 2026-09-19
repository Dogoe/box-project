package com.endel.demobox.quote.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SizingResultDto {
    private double dailyConsumptionKwh;
    private double continuousLoadKw;
    private double peakLoadKw;
    private double recommendedInverterKw;
    private double recommendedBatteryCapacityKwh;
    private double recommendedPanelArrayKwp;
}
