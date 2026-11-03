package com.endel.demobox.quote.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class KitComparisonRowDto {
    private String label;
    private String unit;
    private double kitValue;
    private double targetValue;
    private boolean meets;
    private double diff;
}
