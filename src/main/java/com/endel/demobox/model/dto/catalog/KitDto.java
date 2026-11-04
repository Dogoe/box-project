package com.endel.demobox.model.dto.catalog;

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

    private String imageUrl;

    /** Sum of this kit's panel wattages (kWp), battery capacities (kWh), and inverter ratings (kW). */
    private double totalPanelArrayKwp;
    private double totalBatteryCapacityKwh;
    private double totalInverterPowerKw;

    /** Battery energy actually usable: each battery's kWh times its chemistry's depth of discharge. */
    private double usableBatteryCapacityKwh;

    @NotEmpty
    @Valid
    private List<KitItemDto> items = new ArrayList<>();
}
