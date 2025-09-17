package com.endel.demobox.model.dto;

import lombok.Data;

import java.util.HashSet;
import java.util.Set;

@Data
public class PostDto {
    private Long id;
    private String name;
    private String description;
    private String type;
    private Boolean published;
    private Set<String> photoUrl = new HashSet<>();

}
