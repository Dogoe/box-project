package com.endel.demobox.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.HashSet;
import java.util.Set;

@Entity
@Data
@Table(name = "Posts")
@AllArgsConstructor
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column
    private String name;

    @Column
    private String description;

    @Column
    private String type;

    @Column
    private Boolean published;

    @Column
    @ElementCollection(fetch = FetchType.EAGER)
    private Set<String> photoUrl = new HashSet<>();

    public Post() {

    }

    public Post(String name, String description, String type, Boolean published) {
        this.name = name;
        this.description = description;
        this.type = type;
        this.published = published;

    }
}
