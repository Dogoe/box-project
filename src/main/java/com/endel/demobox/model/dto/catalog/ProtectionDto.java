package com.endel.demobox.model.dto.catalog;

import com.endel.demobox.model.catalog.FuseClass;
import com.endel.demobox.model.catalog.ProtectionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class ProtectionDto {
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

    @NotNull(message = "Elige el tipo de protección")
    private ProtectionType protectionType;

    /** Fuses only; ignored for the other types. */
    private FuseClass fuseClass;

    @Positive(message = "La corriente nominal (A) es obligatoria")
    private double ratedCurrentA;

    @Positive(message = "El voltaje máximo es obligatorio")
    private double maxVoltage;

    @Positive(message = "Los polos deben ser 1 o más")
    private Integer poles;

    /** Read-only, e.g. "125 A · 58 V DC · MRBF". */
    private String specLabel;
}
