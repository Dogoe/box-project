package com.endel.demobox.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "wiring_settings")
@Data
@NoArgsConstructor
public class WiringSettings {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Default one-way run length used to size the battery-to-inverter cable. */
    @Positive
    @Column(nullable = false)
    private double batteryToInverterMeters;

    /** Default one-way run length used to size the panels-to-inverter (MPPT) cable. */
    @Positive
    @Column(nullable = false)
    private double pvToInverterMeters;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
