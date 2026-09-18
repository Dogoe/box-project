package com.endel.demobox.quote;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApplianceTemplateCatalogTest {

    private ApplianceTemplateCatalog catalog;

    @BeforeEach
    void setUp() throws IOException {
        catalog = new ApplianceTemplateCatalog(new ObjectMapper());
    }

    @Test
    void findById_returnsKnownTemplate() {
        var template = catalog.findById("fridge_medium");

        assertTrue(template.isPresent());
        assertTrue(template.get().getWattage() > 0);
    }

    @Test
    void findById_unknownIdReturnsEmpty() {
        assertTrue(catalog.findById("does_not_exist").isEmpty());
    }

    @Test
    void groupedByCategory_coversRefrigeratorsAndAirConditioners() {
        var grouped = catalog.groupedByCategory();

        List<ApplianceTemplate> fridges = grouped.get(ApplianceCategory.REFRIGERATOR);
        List<ApplianceTemplate> acUnits = grouped.get(ApplianceCategory.AIR_CONDITIONER);

        assertFalse(fridges.isEmpty());
        assertFalse(acUnits.isEmpty());
    }
}
