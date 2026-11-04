package com.endel.demobox.model.dto.catalog.compatibility;

import lombok.Data;

@Data
public class ExpansionAspectDto {
    private ExpansionLevel level;
    private String label;
    private int current;
    private int additional;
    /** Admin-only explanation with exact numbers. */
    private String detail;
}
