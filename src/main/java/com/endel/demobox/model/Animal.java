package com.endel.demobox.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "animals")
@Data
public class Animal {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column
    private String name;
}
