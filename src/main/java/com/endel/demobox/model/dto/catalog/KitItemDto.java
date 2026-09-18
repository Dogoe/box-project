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

    @NotNull
    @Positive
    private Integer quantity;
}
