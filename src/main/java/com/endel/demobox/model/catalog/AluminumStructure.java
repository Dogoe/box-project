package com.endel.demobox.model.catalog;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "aluminum_structures")
@DiscriminatorValue("STRUCTURE")
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class AluminumStructure extends Product {
}
