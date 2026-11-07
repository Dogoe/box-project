package com.endel.demobox.model.catalog;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import java.util.List;

@Entity
@Table(name = "aluminum_structures")
@DiscriminatorValue("STRUCTURE")
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class AluminumStructure extends Product {

    @Override
    protected String codeType() {
        return "ESTRUCTURA";
    }

    /** Structures have no specs of their own, so the SKU tells them apart. */
    @Override
    protected List<String> codeSpecs() {
        return List.of(getSku() == null ? "" : getSku());
    }
}
