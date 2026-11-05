package com.endel.demobox.model.catalog;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "kits")
@Data
@NoArgsConstructor
public class Kit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false, unique = true)
    private String name;

    @Column(length = 2000)
    private String description;

    @Column(nullable = false)
    private boolean active = true;

    /** Optional manual override; when null the kit price is the sum of item (product price * quantity). */
    private BigDecimal priceOverride;

    /** Main photo (the first of {@link #imageUrls}); kept as its own column for older rows and readers. */
    private String imageUrl;

    /** Ordered photos; the first is the main one. Use {@link #getPhotos()} / {@link #setPhotos(List)}. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "kit_images", joinColumns = @JoinColumn(name = "kit_id"))
    @OrderColumn(name = "position")
    @Column(name = "url", nullable = false, length = 1000)
    @Fetch(FetchMode.SUBSELECT)
    private List<String> imageUrls = new ArrayList<>();

    /** Photos in display order; kits saved before photo lists existed only have {@code imageUrl}. */
    public List<String> getPhotos() {
        return PhotoUrls.withLegacy(imageUrls, imageUrl);
    }

    public void setPhotos(List<String> photos) {
        // Mutate the managed collection so Hibernate tracks the change.
        imageUrls.clear();
        imageUrls.addAll(photos);
        imageUrl = photos.isEmpty() ? null : photos.get(0);
    }

    @OneToMany(mappedBy = "kit", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<KitItem> items = new ArrayList<>();
}
