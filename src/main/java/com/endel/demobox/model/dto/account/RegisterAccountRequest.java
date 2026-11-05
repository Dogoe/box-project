package com.endel.demobox.model.dto.account;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterAccountRequest {
    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 120)
    private String fullName;

    @NotBlank(message = "El teléfono es obligatorio")
    @Size(max = 20)
    private String phone;

    @NotBlank(message = "El correo es obligatorio")
    @Email(message = "El correo no es válido")
    @Size(max = 200)
    private String email;

    @NotBlank(message = "La contraseña es obligatoria")
    @Size(min = 8, max = 100, message = "La contraseña debe tener al menos 8 caracteres")
    private String password;
}
