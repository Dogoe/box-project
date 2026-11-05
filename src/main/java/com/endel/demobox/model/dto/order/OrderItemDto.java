package com.endel.demobox.model.dto.order;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class OrderItemDto {
    private Long kitId;
    private String kitName;
    private BigDecimal unitPriceMxn;
    private int quantity;
    private BigDecimal lineTotalMxn;
}
