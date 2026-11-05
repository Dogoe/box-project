package com.endel.demobox.model.dto.catalog;

/**
 * One photo in the kit page gallery. {@code productId} is null for the kit's own photos; it lets
 * "Qué incluye" jump to a product's photos.
 */
public record KitPhotoDto(String url, String caption, Long productId) {
}
