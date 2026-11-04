package com.endel.demobox.model.dto.catalog.compatibility;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CompatibilityCheckDto {
    private CompatibilitySeverity severity;
    private String message;
}
