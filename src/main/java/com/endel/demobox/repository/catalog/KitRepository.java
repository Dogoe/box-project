package com.endel.demobox.repository.catalog;

import com.endel.demobox.model.catalog.Kit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface KitRepository extends JpaRepository<Kit, Long> {
    Optional<Kit> findByName(String name);
}
