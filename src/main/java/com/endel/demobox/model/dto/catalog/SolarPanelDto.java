package com.endel.demobox.model.dto.catalog;

import com.endel.demobox.model.catalog.PanelTechnology;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class SolarPanelDto {
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
    private double wattage;

    @NotNull
    private PanelTechnology technology;

    private double efficiencyPercent;

    private double voc;

    private double isc;
}
