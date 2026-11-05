package com.endel.demobox.model.dto.catalog;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One product photo in a kit's automatic collage, with how many of that product the kit includes. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KitCollageTileDto {
    private String imageUrl;
    private String productName;
    private int quantity;
}
