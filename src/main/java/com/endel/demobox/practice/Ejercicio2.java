package com.endel.demobox.practice;

import java.util.*;
import java.util.stream.Collectors;

public class Ejercicio2 {
     public static void main(String[] args){

         List<Integer> list = Arrays.asList(1,4,3,5,7,9,8,8,5,3);

         Map<Integer,Integer> mapSave = new HashMap<>();

         for(Integer num: list){
             mapSave.put(num, mapSave.getOrDefault(num,0) + 1);
         }

         for(Map.Entry<Integer, Integer> entry : mapSave.entrySet()){
             if(entry.getValue() > 1){
                 System.out.println(entry.getKey());
             }
         }


    }
}

