package com.endel.demobox.model.dto.catalog;

import com.endel.demobox.model.catalog.InverterType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class InverterDto {
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

    /** Main photo: the first of {@link #imageUrls}. Still accepted alone from older clients. */
    private String imageUrl;

    /** Ordered photos (max 8), the first is the main one. Null when the client only sends {@link #imageUrl}. */
    private List<String> imageUrls;

    @Pattern(regexp = "^(https?://\\S+)?$", message = "La URL de referencia debe empezar con http:// o https://")
    private String referenceUrl;

    private boolean active = true;

    @Positive
    private double ratedPowerKw;

    @Positive
    private double surgePowerKw;

    @NotNull
    private InverterType inverterType;

    /** False for inverters without a solar charger: the MPPT fields are ignored and cleared. */
    private Boolean builtInCharger = true;

    private Integer mpptChannels;

    private double maxInputVoltage;

    @Positive
    private Double batteryVoltage;

    @Positive
    private Double mpptMinVoltage;

    @Positive
    private Double mpptMaxChargeCurrentA;

    @Positive
    private Double mpptMaxInputCurrentA;

    @Positive
    private Double maxPvInputPowerW;

    // Read-only, derived: mpptMaxChargeCurrentA x batteryVoltage.
    private double maxChargePowerW;

    // Read-only: maxPvInputPowerW when captured, otherwise estimated from charge power.
    private double effectiveMaxPvPowerW;
    private boolean maxPvPowerEstimated;
}
