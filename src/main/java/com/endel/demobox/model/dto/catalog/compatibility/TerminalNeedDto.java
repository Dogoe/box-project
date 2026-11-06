package com.endel.demobox.model.dto.catalog.compatibility;

import lombok.Data;

/** Lugs of one kind (wire gauge + stud size) the kit's cables need, and whether the kit includes them. */
@Data
public class TerminalNeedDto {
    /** e.g. "2 AWG". */
    private String gaugeLabel;
    /** e.g. "M8". */
    private String sizeLabel;
    private int quantity;
    /** Where they go, e.g. "2 lado batería, 2 lado inversor, 2 breaker DC". */
    private String places;
    private boolean covered;
    /** Kit products that cover them, e.g. "1 × Terminal 2 AWG M8 (bolsa de 10)". */
    private String coveredBy;
    /** Cheapest catalog pack(s) that cover what's missing. */
    private SuggestedItemDto suggestion;
}
