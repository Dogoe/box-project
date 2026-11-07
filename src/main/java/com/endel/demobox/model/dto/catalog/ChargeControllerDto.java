package com.endel.demobox.model.dto.catalog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

@Data
public class ChargeControllerDto {
    private Long id;

    @NotBlank
    private String sku;

    /** Short identifier built from the type, specs and price (computed by the server), e.g. "PANEL-550W-49.5Voc-MC4-$2450". */
    private String code;

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

    @NotEmpty(message = "Marca al menos un voltaje de batería (12, 24 o 48 V)")
    private Set<Integer> supportedBatteryVoltages = new TreeSet<>();

    @Positive(message = "La corriente máxima de carga es obligatoria")
    private double maxChargeCurrentA;

    @Positive(message = "El voltaje PV máximo es obligatorio")
    private double maxInputVoltage;

    @Positive
    private Double mpptMinVoltage;

    @Positive
    private Double mpptMaxInputCurrentA;

    @Positive
    private Double maxPvInputPowerW;

    @Positive
    private Integer mpptChannels;
}
