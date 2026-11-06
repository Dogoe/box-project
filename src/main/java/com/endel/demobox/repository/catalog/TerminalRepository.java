package com.endel.demobox.repository.catalog;

import com.endel.demobox.model.catalog.Terminal;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TerminalRepository extends JpaRepository<Terminal, Long> {
}
