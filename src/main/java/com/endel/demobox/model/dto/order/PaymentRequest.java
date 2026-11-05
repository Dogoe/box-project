package com.endel.demobox.model.dto.order;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PaymentRequest {
    @NotNull(message = "Indica el monto recibido")
    @Positive(message = "El monto recibido debe ser mayor a 0")
    private BigDecimal amountMxn;

    @Size(max = 500)
    private String note;
}
