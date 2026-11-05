package com.endel.demobox.model.dto.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateAccountRequest {
    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 120)
    private String fullName;

    @NotBlank(message = "El teléfono es obligatorio")
    @Size(max = 20)
    private String phone;

    @Size(max = 200)
    private String defaultStreet;

    @Size(max = 120)
    private String defaultNeighborhood;

    @Pattern(regexp = "^(\\d{5})?$", message = "El código postal debe tener 5 dígitos")
    private String defaultPostalCode;

    @Size(max = 500)
    private String defaultAddressReferences;
}
