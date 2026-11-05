package com.endel.demobox.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class StoreSettingsDto {
    /** Optional; 10 digits (a leading +52 / 52 is accepted and stripped). */
    @Size(max = 20)
    private String whatsappNumber;

    @Email(message = "El correo para avisos no es válido")
    @Size(max = 200)
    private String notificationEmail;

    @NotBlank(message = "Las instrucciones de pago son obligatorias")
    @Size(max = 1000)
    private String paymentInstructions;

    private LocalDateTime updatedAt;
}
