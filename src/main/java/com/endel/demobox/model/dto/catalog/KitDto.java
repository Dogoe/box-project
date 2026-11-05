package com.endel.demobox.model.dto.catalog;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class KitDto {
    private Long id;

    @NotBlank
    private String name;

    private String description;

    private boolean active = true;

    private BigDecimal priceOverride;

    /** Sum of this kit's item costs (product price x quantity) - not the customer-facing price. */
    private BigDecimal computedPrice;

    /** The customer-facing price: priceOverride if set, otherwise computedPrice with the configured profit margin applied. */
    private BigDecimal sellPrice;

    /** Main photo: the first of {@link #imageUrls}. Still accepted alone from older clients. */
    private String imageUrl;

    /** Ordered photos (max 8), the first is the main one. Null when the client only sends {@link #imageUrl}. */
    private List<String> imageUrls;

    /** URL-friendly name for the kit page, e.g. "kit-cabana-3-kw" in /kits/12/kit-cabana-3-kw (read-only). */
    private String slug;

    /**
     * Up to 4 product photos (inverter or controller, panel, battery) used as the kit's picture when it
     * has no photo of its own (read-only).
     */
    private List<KitCollageTileDto> collage = new ArrayList<>();

    /** Sum of this kit's panel wattages (kWp), battery capacities (kWh), and inverter ratings (kW). */
    private double totalPanelArrayKwp;
    private double totalBatteryCapacityKwh;
    private double totalInverterPowerKw;

    /** Battery energy actually usable: each battery's kWh times its chemistry's depth of discharge. */
    private double usableBatteryCapacityKwh;

    @NotEmpty
    @Valid
    private List<KitItemDto> items = new ArrayList<>();

    /** Picture that represents the kit: its own main photo, else the first collage photo; null when there's none. */
    @JsonIgnore
    public String getCoverImageUrl() {
        if (imageUrl != null && !imageUrl.isBlank()) {
            return imageUrl;
        }
        return collage.isEmpty() ? null : collage.get(0).getImageUrl();
    }
}
