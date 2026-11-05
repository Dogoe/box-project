package com.endel.demobox.model.dto.catalog;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class KitItemDto {
    private Long id;

    @NotNull
    private Long productId;

    private String productSku;

    private String productName;

    private BigDecimal unitPrice;

    /** PANEL, BATTERY, INVERTER, CHARGE_CONTROLLER, CABLE, STRUCTURE or OTHER - which catalog subtype the referenced product is. */
    private String productType;

    /** Formatted headline spec for display, e.g. "400 W", "5 kWh", "2 kW". */
    private String specLabel;

    @NotNull
    @Positive
    private Integer quantity;
}
