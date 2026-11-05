package com.endel.demobox.model.catalog;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "products")
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "product_type")
@Data
@NoArgsConstructor
public abstract class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false, unique = true)
    private String sku;

    @NotBlank
    @Column(nullable = false)
    private String name;

    private String brand;

    @Column(length = 2000)
    private String description;

    @NotNull
    @PositiveOrZero
    @Column(nullable = false)
    private BigDecimal price;

    @PositiveOrZero
    @Column(nullable = false)
    private Integer stockQuantity = 0;

    /** Main photo (the first of {@link #imageUrls}); kept as its own column for older rows and readers. */
    private String imageUrl;

    /** Ordered photos; the first is the main one. Use {@link #getPhotos()} / {@link #setPhotos(List)}. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "product_images", joinColumns = @JoinColumn(name = "product_id"))
    @OrderColumn(name = "position")
    @Column(name = "url", nullable = false, length = 1000)
    @Fetch(FetchMode.SUBSELECT)
    private List<String> imageUrls = new ArrayList<>();

    /** Photos in display order; products saved before photo lists existed only have {@code imageUrl}. */
    public List<String> getPhotos() {
        return PhotoUrls.withLegacy(imageUrls, imageUrl);
    }

    public void setPhotos(List<String> photos) {
        // Mutate the managed collection so Hibernate tracks the change.
        imageUrls.clear();
        imageUrls.addAll(photos);
        imageUrl = photos.isEmpty() ? null : photos.get(0);
    }

    /** Optional link to a reference listing (Amazon, Mercado Libre...); admin-only. */
    @Column(length = 2000)
    private String referenceUrl;

    @Column(nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
