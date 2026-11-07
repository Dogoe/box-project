package com.endel.demobox.model.dto.catalog;

import com.endel.demobox.model.catalog.TerminalSize;
import com.endel.demobox.model.catalog.BatteryChemistry;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class BatteryDto {
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

    @NotNull
    @Positive
    private Double capacityAh;

    @Positive
    private double voltage;

    private BatteryChemistry chemistry;

    private Integer cycleLife;
    /** Optional, lithium only: the BMS's continuous discharge limit (A). Empty = capacity in amps (1C). */
    @Positive(message = "La corriente del BMS debe ser mayor que 0")
    private Double bmsMaxDischargeA;
    /** Optional: output breaker built into the battery (A); then no separate main fuse/switch is needed. */
    @Positive(message = "El breaker integrado debe ser mayor que 0")
    private Double integratedBreakerA;
    /** Stud size of its connection terminals: M8 (default when missing), M6 or M10. */
    private TerminalSize terminalSize;

    // Read-only, derived from capacityAh/voltage/chemistry.
    private double capacityKwh;
    private double depthOfDischargePercent;
    private double maxDischargeCurrentA;
}
