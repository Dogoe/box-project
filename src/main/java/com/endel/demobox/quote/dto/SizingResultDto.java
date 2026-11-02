package com.endel.demobox.quote.dto;

import com.endel.demobox.model.dto.catalog.KitDto;
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
    private KitDto recommendedKit;
}
