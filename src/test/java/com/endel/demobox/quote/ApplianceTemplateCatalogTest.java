package com.endel.demobox.quote;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApplianceTemplateCatalogTest {

    private ApplianceTemplateCatalog catalog;

    @BeforeEach
    void setUp() throws IOException {
        catalog = new ApplianceTemplateCatalog(new ObjectMapper());
    }

    @Test
    void findById_returnsKnownTemplateInSpanishByDefault() {
        var template = catalog.findById("fridge_medium", "es");

        assertTrue(template.isPresent());
        assertTrue(template.get().getWattage() > 0);
        assertTrue(template.get().getLabel().startsWith("Refrigerador"));
    }

    @Test
    void findById_returnsTheEnglishLabelForTheSameIdWhenAskedForEnglish() {
        var esTemplate = catalog.findById("fridge_medium", "es").orElseThrow();
        var enTemplate = catalog.findById("fridge_medium", "en").orElseThrow();

        assertTrue(enTemplate.getLabel().startsWith("Refrigerator"));
        // same physical appliance regardless of language - specs must match
        assertEquals(esTemplate.getWattage(), enTemplate.getWattage());
        assertEquals(esTemplate.getDefaultHoursPerDay(), enTemplate.getDefaultHoursPerDay());
    }

    @Test
    void findById_fallsBackToSpanishForAnUnrecognizedLanguage() {
        var template = catalog.findById("fridge_medium", "fr");

        assertTrue(template.isPresent());
        assertTrue(template.get().getLabel().startsWith("Refrigerador"));
    }

    @Test
    void findById_unknownIdReturnsEmpty() {
        assertTrue(catalog.findById("does_not_exist", "es").isEmpty());
    }

    @Test
    void groupedByCategory_coversRefrigeratorsAndAirConditionersInBothLanguages() {
        for (String lang : List.of("es", "en")) {
            var grouped = catalog.groupedByCategory(lang);

            List<ApplianceTemplate> fridges = grouped.get(ApplianceCategory.REFRIGERATOR);
            List<ApplianceTemplate> acUnits = grouped.get(ApplianceCategory.AIR_CONDITIONER);

            assertFalse(fridges.isEmpty());
            assertFalse(acUnits.isEmpty());
        }
    }
}
