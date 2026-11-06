package com.endel.demobox.model.dto.catalog.compatibility;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** A catalog product the kit is missing, with how many to add ("Agregar sugeridos" in the admin). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SuggestedItemDto {
    private Long productId;
    private String sku;
    private String name;
    private int quantity;
    private BigDecimal unitPrice;
    /** What it's for, e.g. "Batería – inversor" or "Fusible principal de batería". */
    private String reason;
}
