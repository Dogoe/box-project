package com.endel.demobox.model.dto.catalog;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class KitDto {
    private Long id;

    @NotBlank
    private String name;

    private String description;

    private boolean active = true;

    private BigDecimal priceOverride;

    private BigDecimal computedPrice;

    @NotEmpty
    @Valid
    private List<KitItemDto> items = new ArrayList<>();
}
