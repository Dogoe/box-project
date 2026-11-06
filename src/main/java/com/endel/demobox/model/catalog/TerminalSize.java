package com.endel.demobox.model.catalog;

import lombok.Getter;

/** Stud (bolt) size a cable lug bolts onto. M8 is the standard across the store's equipment. */
@Getter
public enum TerminalSize {
    M6("M6"),
    M8("M8"),
    M10("M10");

    private final String label;

    TerminalSize(String label) {
        this.label = label;
    }

    /** What a missing value means: the standard M8. */
    public static TerminalSize orDefault(TerminalSize size) {
        return size == null ? M8 : size;
    }
}
