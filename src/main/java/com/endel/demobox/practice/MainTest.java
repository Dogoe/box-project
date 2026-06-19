package com.endel.demobox.practice;

import java.util.*;
import java.util.stream.Collectors;

public class MainTest {
     public static void main(String[] args){
            System.out.println("HELLO WORDL");


         Map<String,Employee> employeeMap = new LinkedHashMap<>();

         Employee empl1 = new Employee("1", 1.0F,"Angel");
         Employee empl2 = new Employee("2", 50.0F,"Lugardo");
         Employee empl3 = new Employee("3", 99.500F,"Angel");
         Employee empl4 = new Employee("4", 10.0F,"Edgar");

         employeeMap.put(empl1.getName(),empl1);
         employeeMap.put(empl2.getName(),empl2);
         employeeMap.put(empl3.getName(),empl3);
         employeeMap.put(empl4.getName(),empl4);

         //
         List<Employee> filter = employeeMap.values().stream()
                 .sorted(Comparator.comparing(Employee::getName).thenComparingDouble(Employee::getSalary))
                 .toList();

         Map<String, Employee> filterMap = employeeMap.values().stream()
                 .sorted(Comparator.comparing(Employee::getName)
                         .thenComparingDouble(Employee::getSalary))
                 .collect(Collectors.toMap(
                         Employee::getName,
                         employee -> employee,
                         (e1, e2) -> e1,
                         LinkedHashMap::new
                 ));



         System.out.println(filter);


    }
}

