package com.endel.demobox.model.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ProfitMarginDto {

    @NotNull
    @PositiveOrZero
    private BigDecimal marginPercent;

    private LocalDateTime updatedAt;
}
