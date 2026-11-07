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
    /** The part of the kit it is about. */
    private KitSection section = KitSection.GENERAL;

    public CompatibilityCheckDto(CompatibilitySeverity severity, String message) {
        this.severity = severity;
        this.message = message;
    }
}
