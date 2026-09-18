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

@Entity
@Table(name = "solar_panels")
@DiscriminatorValue("PANEL")
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class SolarPanel extends Product {

    @Positive
    @Column(nullable = false)
    private double wattage;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PanelTechnology technology;

    private double efficiencyPercent;

    private double voc;

    private double isc;
}
