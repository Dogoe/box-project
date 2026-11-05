package com.endel.demobox.model.dto.account;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {
    @NotBlank(message = "Escribe tu correo")
    private String email;

    @NotBlank(message = "Escribe tu contraseña")
    private String password;
}
