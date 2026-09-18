package com.endel.demobox.repository.catalog;

import com.endel.demobox.model.catalog.Kit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KitRepository extends JpaRepository<Kit, Long> {
}
