package com.endel.demobox.model.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Fuses, breakers and disconnects. A kit lists them like any other product (so they count in its
 * cost); the kit review says which ones the kit needs and whether these ratings cover them.
 */
@Entity
@Table(name = "protections")
@DiscriminatorValue("PROTECTION")
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class Protection extends Product {

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProtectionType protectionType;

    /** Only for fuses. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private FuseClass fuseClass;

    @Positive
    @Column(nullable = false)
    private double ratedCurrentA;

    /** Highest voltage it's rated for: DC for fuses, DC breakers and disconnects; AC for AC breakers. */
    @Positive
    @Column(nullable = false)
    private double maxVoltage;

    private Integer poles;

    /**
     * Stud size of its connection terminals (M8 unless set). Nullable so rows saved before this column existed
     * still load; {@link #getTerminalSize()} reads them as M8.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "terminal_size", length = 10)
    private TerminalSize terminalSize = TerminalSize.M8;

    public TerminalSize getTerminalSize() {
        return TerminalSize.orDefault(terminalSize);
    }

    public int getPolesOrOne() {
        return poles == null || poles < 1 ? 1 : poles;
    }

    /** Short spec for lists, e.g. "125 A · 58 V DC · MRBF". */
    public String getSpecLabel() {
        String volts = fmt(maxVoltage) + (protectionType == ProtectionType.AC_BREAKER ? " V AC" : " V DC");
        String label = fmt(ratedCurrentA) + " A · " + volts;
        if (protectionType == ProtectionType.FUSE && fuseClass != null) {
            label += " · " + fuseClass.getLabel();
        }
        if (getPolesOrOne() > 1) {
            label += " · " + getPolesOrOne() + " polos";
        }
        return label;
    }

    private static String fmt(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }
}
