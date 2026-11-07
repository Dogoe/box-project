package com.endel.demobox.model.dto.catalog;

import com.endel.demobox.model.catalog.TerminalSize;
import com.endel.demobox.model.catalog.WireGauge;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class TerminalDto {
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

    @NotNull(message = "Elige el calibre del cable que lleva la terminal")
    private WireGauge gauge;

    /** M8 when missing. */
    private TerminalSize terminalSize;

    /** 1 when missing (sold loose). */
    @Positive(message = "Las piezas por paquete deben ser 1 o más")
    private Integer piecesPerPack;

    /** Read-only, e.g. "2 AWG · M8 · bolsa de 10". */
    private String specLabel;
}
