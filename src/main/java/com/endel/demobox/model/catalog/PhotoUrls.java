package com.endel.demobox.model.catalog;

import com.endel.demobox.exception.BadRequestException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** Rules for the ordered photo lists of products and kits. */
public final class PhotoUrls {
    public static final int MAX_PHOTOS = 8;

    private PhotoUrls() {
    }

    /**
     * The list to store: {@code urls} when the client sent one, otherwise the single legacy
     * {@code imageUrl} field (older clients). Blank entries and repeats are dropped.
     */
    public static List<String> resolve(List<String> urls, String singleUrl) {
        List<String> source = urls != null ? urls : (singleUrl == null ? List.of() : List.of(singleUrl));
        LinkedHashSet<String> cleaned = new LinkedHashSet<>();
        for (String url : source) {
            if (url == null || url.isBlank()) {
                continue;
            }
            String trimmed = url.trim();
            if (!trimmed.matches("(?i)^https?://\\S+$")) {
                throw new BadRequestException("Las fotos deben ser links que empiecen con http:// o https://");
            }
            cleaned.add(trimmed);
        }
        if (cleaned.size() > MAX_PHOTOS) {
            throw new BadRequestException("Máximo " + MAX_PHOTOS + " fotos; quita alguna.");
        }
        return new ArrayList<>(cleaned);
    }

    /** Stored list, or the single photo saved before photo lists existed. */
    static List<String> withLegacy(List<String> stored, String legacyImageUrl) {
        if (!stored.isEmpty()) {
            return List.copyOf(stored);
        }
        return legacyImageUrl == null || legacyImageUrl.isBlank() ? List.of() : List.of(legacyImageUrl);
    }
}
