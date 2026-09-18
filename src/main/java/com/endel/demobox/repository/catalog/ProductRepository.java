package com.endel.demobox.repository.catalog;

import com.endel.demobox.model.catalog.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {
}
