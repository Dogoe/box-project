package com.endel.demobox.model.dto.catalog.compatibility;

import com.endel.demobox.model.catalog.Protection;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

/** One protection the kit needs, and whether the kit's products cover it. */
@Data
public class ProtectionCheckDto {
    /** e.g. "Fusible principal de batería". */
    private String label;
    /** What it must meet, e.g. "Fusible de 110 a 115 A, 57.6 V DC o más". */
    private String requirement;
    private int quantity;
    private boolean covered;
    /** Kit products that cover it, e.g. "1 × MRBF 125 A 58 V", or the battery's built-in breaker. */
    private String coveredBy;
    /** Why a protection in the kit doesn't qualify, e.g. "ANL 125 A: aguanta 32 V y se necesitan 57.6 V". */
    private String problem;
    /** Cheapest catalog product that meets it, when it isn't covered. */
    private SuggestedItemDto suggestion;
    /** Where it goes in the system. */
    private ProtectionSlot slot;
    /** True when the battery's own breaker does this job (nothing extra to install). */
    @JsonIgnore
    private boolean builtIntoBattery;
    /** The product that does the job: the kit's own, else the suggested one; null when neither exists. */
    @JsonIgnore
    private Protection product;
}
