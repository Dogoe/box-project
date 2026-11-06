package com.endel.demobox.model.dto.catalog.transfer;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** A kit as written in a catalog file: its products are referenced by SKU, not by database id. */
@Data
public class KitTransferDto {
    private String name;
    private String description;
    /** Missing means true, like a new kit in the admin. */
    private Boolean active;
    private BigDecimal priceOverride;
    private List<String> imageUrls;
    /** Single photo, accepted like in the admin API when there is no imageUrls list. */
    private String imageUrl;
    private List<Item> items;

    @Data
    public static class Item {
        private String sku;
        private Integer quantity;
    }
}
