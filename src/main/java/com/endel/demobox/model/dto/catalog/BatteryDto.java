package com.endel.demobox.model.dto.catalog;

import com.endel.demobox.model.catalog.BatteryChemistry;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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

    @Pattern(regexp = "^(https?://\\S+)?$", message = "La URL de referencia debe empezar con http:// o https://")
    private String referenceUrl;

    private boolean active = true;

    @NotNull
    @Positive
    private Double capacityAh;

    @Positive
    private double voltage;

    private BatteryChemistry chemistry;

    private Integer cycleLife;

    // Read-only, derived from capacityAh/voltage/chemistry.
    private double capacityKwh;
    private double depthOfDischargePercent;
    private double maxDischargeCurrentA;
}
