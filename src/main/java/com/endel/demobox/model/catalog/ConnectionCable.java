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
}
