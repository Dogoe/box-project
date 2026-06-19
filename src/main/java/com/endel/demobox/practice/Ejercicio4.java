package com.endel.demobox.practice;

import java.util.*;

public class Ejercicio4 {
     public static void main(String[] args){
         Scanner sc = new Scanner(System.in);

         Integer first = sc.nextInt();
         Integer second = sc.nextInt();
         Integer third = sc.nextInt();
         Integer four = sc.nextInt();

         //Second bigger number of list
         List<Integer> list = Arrays.asList(first,second,third,four);

         if(list.size()==1){
             System.out.println("ERROR 500");
             return;
         }
         Collections.sort(list);
         System.out.println(list.get(list.size()-2));

        // Map<String, List<Employee>> departmentMap = employeeList.stream().collect(Collectors.groupingBy(Employee::getDepartment));
         Map<String, Double> departmentSum = new HashMap<>();


         //departmentMap.entrySet().forEach(e -> {
         //    departmentSum.put(e.getKey(), e.getValue().stream().reduce(0.0,(acc, employee) -> acc + employee.getSalary(), Double::sum));
        // });
    }
}

