package com.endel.demobox.model.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import java.util.List;

@Entity
@Table(name = "connection_cables")
@DiscriminatorValue("CABLE")
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class ConnectionCable extends Product {

    @Positive
    @Column(nullable = false)
    private double lengthMeters;

    // Nullable at the DB level so ddl-auto can add it to existing rows.
    @Enumerated(EnumType.STRING)
    private WireGauge gauge;

    @Override
    protected String codeType() {
        return "CABLE";
    }

    /** Gauge and length. */
    @Override
    protected List<String> codeSpecs() {
        return java.util.Arrays.asList(ProductCode.gauge(gauge), ProductCode.num(lengthMeters) + "M");
    }
}
