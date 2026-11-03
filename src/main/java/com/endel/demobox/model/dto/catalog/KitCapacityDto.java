package com.endel.demobox.model.dto.catalog;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class KitCapacityDto {
    private double estimatedMaxDailyKwh;
    private double estimatedMaxContinuousKw;
    private List<String> compatibleAppliances = new ArrayList<>();
}
