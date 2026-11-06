package com.endel.demobox.model.catalog;

import lombok.Getter;

/** Which job a protection product does in a solar system. */
@Getter
public enum ProtectionType {
    FUSE("Fusible"),
    DC_BREAKER("Breaker / interruptor DC"),
    PV_DISCONNECT("Desconectador DC de paneles"),
    AC_BREAKER("Breaker AC");

    private final String label;

    ProtectionType(String label) {
        this.label = label;
    }
}
