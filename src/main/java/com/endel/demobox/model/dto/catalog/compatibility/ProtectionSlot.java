package com.endel.demobox.model.dto.catalog.compatibility;

/** Where a protection goes in the system, so the wiring diagram can draw each one in its place. */
public enum ProtectionSlot {
    MAIN_FUSE,
    BATTERY_SWITCH,
    CONTROLLER,
    PV_DISCONNECT,
    PV_STRING_FUSE,
    AC_BREAKER
}
