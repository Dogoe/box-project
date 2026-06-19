package com.endel.demobox.practice;

import java.util.*;

public class Ejercicio3 {
     public static void main(String[] args){

         //First non repeted
         String string = "EEHOLA";

         char[] stringArray = string.toCharArray();

         Map<Character,Integer> characterMap = new LinkedHashMap<>();

         for(char c: stringArray){
             characterMap.put(c, characterMap.getOrDefault(c,0) + 1);
         }

         for(Map.Entry<Character, Integer> entry : characterMap.entrySet()){
             if(entry.getValue() == 1){
                 System.out.println(entry.getKey());
                 break;
             }
         }


    }
}

