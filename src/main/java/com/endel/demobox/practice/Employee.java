package com.endel.demobox.practice;


import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Employee {
    private String id;
    private Float salary;
    private String name;
}