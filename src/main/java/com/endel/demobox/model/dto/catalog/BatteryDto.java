package com.endel.demobox.model.dto.catalog;

import com.endel.demobox.model.catalog.BatteryChemistry;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class BatteryDto {
    private Long id;

    @NotBlank
    private String sku;

    @NotBlank
    private String name;

    private String brand;

    private String description;

    @NotNull
    @PositiveOrZero
    private BigDecimal price;

    @PositiveOrZero
    private Integer stockQuantity;

    private String imageUrl;

    private boolean active = true;

    @Positive
    private double capacityKwh;

    @Positive
    private double voltage;

    @NotNull
    private BatteryChemistry chemistry;

    private double depthOfDischargePercent;

    private Integer cycleLife;
}
