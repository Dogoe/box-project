package com.endel.demobox.repository.catalog;

import com.endel.demobox.model.catalog.Battery;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BatteryRepository extends JpaRepository<Battery, Long> {
}
