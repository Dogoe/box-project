package com.endel.demobox.model.catalog;

import lombok.Getter;

/**
 * Fuse families, which differ in how much short-circuit current they can safely interrupt: a large
 * lithium bank needs Class T; MRBF and ANL suit smaller banks within their voltage rating; gPV fuses
 * protect panel strings.
 */
@Getter
public enum FuseClass {
    MRBF("MRBF"),
    ANL("ANL"),
    CLASS_T("Clase T"),
    GPV("gPV (de paneles)"),
    OTHER("Otro");

    private final String label;

    FuseClass(String label) {
        this.label = label;
    }
}
