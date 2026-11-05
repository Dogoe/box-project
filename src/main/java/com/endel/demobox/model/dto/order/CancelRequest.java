package com.endel.demobox.model.dto.order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CancelRequest {
    @NotBlank(message = "Indica el motivo de la cancelación")
    @Size(max = 500)
    private String reason;
}
