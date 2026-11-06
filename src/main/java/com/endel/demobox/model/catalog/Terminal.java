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
 * Crimp cable lugs, sold apart from the cables: a barrel for one wire gauge and an eye for one stud
 * size, usually in packs. The kit review says how many of each a kit needs.
 */
@Entity
@Table(name = "terminals")
@DiscriminatorValue("TERMINAL")
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class Terminal extends Product {

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private WireGauge gauge;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "terminal_size", nullable = false, length = 10)
    private TerminalSize terminalSize = TerminalSize.M8;

    /** Lugs per pack (1 when sold loose). */
    @Positive
    @Column(nullable = false)
    private int piecesPerPack = 1;

    /** e.g. "2 AWG · M8 · bolsa de 10". */
    public String getSpecLabel() {
        return gauge.getLabel() + " · " + terminalSize.getLabel() + (piecesPerPack > 1 ? " · bolsa de " + piecesPerPack : "");
    }
}
