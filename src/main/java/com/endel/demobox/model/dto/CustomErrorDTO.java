package com.endel.demobox.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CustomErrorDTO {
    private String name;
    private String message;
}
