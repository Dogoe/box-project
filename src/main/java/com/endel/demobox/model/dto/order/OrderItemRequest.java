package com.endel.demobox.model.dto.order;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class OrderItemRequest {
    @NotNull
    private Long kitId;

    @NotNull
    @Min(1)
    @Max(10)
    private Integer quantity;
}
