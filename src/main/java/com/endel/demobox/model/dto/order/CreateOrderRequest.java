package com.endel.demobox.model.dto.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** Guest checkout. Only kit ids and quantities come from the client; prices are always recalculated. */
@Data
public class CreateOrderRequest {
    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 120)
    private String customerName;

    @NotBlank(message = "El teléfono es obligatorio")
    @Size(max = 20)
    private String customerPhone;

    @Email(message = "El correo no es válido")
    @Size(max = 200)
    private String customerEmail;

    @NotBlank(message = "La calle y número son obligatorios")
    @Size(max = 200)
    private String street;

    @NotBlank(message = "La colonia es obligatoria")
    @Size(max = 120)
    private String neighborhood;

    @NotBlank(message = "El código postal es obligatorio")
    @Pattern(regexp = "\\d{5}", message = "El código postal debe tener 5 dígitos")
    private String postalCode;

    @Size(max = 500)
    private String addressReferences;

    @Size(max = 1000)
    private String notes;

    @NotEmpty(message = "El carrito está vacío")
    @Size(max = 20)
    private List<@Valid OrderItemRequest> items;
}
