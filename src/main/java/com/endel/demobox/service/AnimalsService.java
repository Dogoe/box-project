package com.endel.demobox.service;

import com.endel.demobox.repository.AnimalRepository;
import org.springframework.stereotype.Service;

@Service
public class AnimalsService {

    private final AnimalRepository animalRepository;

    public AnimalsService(AnimalRepository animalRepository){
        this.animalRepository = animalRepository;
    }

}
