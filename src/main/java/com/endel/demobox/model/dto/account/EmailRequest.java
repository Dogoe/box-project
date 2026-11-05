package com.endel.demobox.model.dto.account;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class EmailRequest {
    @NotBlank(message = "Escribe tu correo")
    @Email(message = "El correo no es válido")
    @Size(max = 200)
    private String email;
}
