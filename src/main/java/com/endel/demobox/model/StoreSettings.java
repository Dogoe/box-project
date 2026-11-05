package com.endel.demobox.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "store_settings")
@Data
@NoArgsConstructor
public class StoreSettings {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 10-digit Mexican number customers message about their order; empty hides the WhatsApp button. */
    @Column(length = 10)
    private String whatsappNumber;

    /** Where new-order alerts are emailed; empty means no store email. */
    @Column(length = 200)
    private String notificationEmail;

    /** Shown to the customer after checkout. */
    @Column(nullable = false, length = 1000)
    private String paymentInstructions;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
