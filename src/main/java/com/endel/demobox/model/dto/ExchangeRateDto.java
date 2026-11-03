package com.endel.demobox.model.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ExchangeRateDto {

    @NotNull
    @Positive
    private BigDecimal usdToMxnRate;

    private LocalDateTime updatedAt;
}
