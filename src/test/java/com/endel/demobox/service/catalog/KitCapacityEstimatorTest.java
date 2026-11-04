package com.endel.demobox.service.catalog;

import com.endel.demobox.model.dto.catalog.KitCapacityDto;
import com.endel.demobox.model.dto.catalog.KitDto;
import com.endel.demobox.quote.ApplianceCategory;
import com.endel.demobox.quote.ApplianceTemplate;
import com.endel.demobox.quote.ApplianceTemplateCatalog;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KitCapacityEstimatorTest {

    private static ApplianceTemplate template(String id, double wattage, double hours, ApplianceCategory category) {
        ApplianceTemplate template = new ApplianceTemplate();
        template.setId(id);
        template.setCategory(category);
        template.setLabel(id);
        template.setWattage(wattage);
        template.setDefaultHoursPerDay(hours);
        return template;
    }

    private static KitDto kit(double panelKwp, double batteryKwh, double inverterKw) {
        KitDto dto = new KitDto();
        dto.setId(1L);
        dto.setTotalPanelArrayKwp(panelKwp);
        dto.setTotalBatteryCapacityKwh(batteryKwh);
        dto.setUsableBatteryCapacityKwh(batteryKwh * 0.8);
        dto.setTotalInverterPowerKw(inverterKw);
        return dto;
    }

    private static KitCapacityEstimator estimatorWith(ApplianceTemplate... templates) {
        ApplianceTemplateCatalog catalog = mock(ApplianceTemplateCatalog.class);
        when(catalog.groupedByCategory("es")).thenReturn(Map.of(ApplianceCategory.OTHER, List.of(templates)));
        return new KitCapacityEstimator(catalog);
    }

    @Test
    void nightAndDayBudgetsAreComputedIndependentlyInsteadOfTakingTheMinimum() {
        ApplianceTemplateCatalog catalog = mock(ApplianceTemplateCatalog.class);
        when(catalog.groupedByCategory("es")).thenReturn(Map.of());
        KitCapacityEstimator estimator = new KitCapacityEstimator(catalog);

        // night (battery): 10 kWh * 0.8 * 0.95 = 7.6 kWh
        // day (panels): 2.0 kWp * 6.0 peak sun hours * 0.80 = 9.6 kWh
        // surplus: 9.6 - 7.6 = 2.0 kWh
        KitCapacityDto result = estimator.estimate(kit(2.0, 10.0, 3.0), "es");

        assertEquals(7.6, result.getNightBudgetKwh(), 0.001);
        assertEquals(9.6, result.getDayBudgetKwh(), 0.001);
        assertEquals(2.0, result.getDaySurplusKwh(), 0.001);
        assertEquals(2.73, result.getEstimatedMaxContinuousKw(), 0.01); // 3.0 / 1.10
    }

    @Test
    void noBatteryMeansNoNightBudgetAndTheFullDayGenerationIsSurplus() {
        ApplianceTemplateCatalog catalog = mock(ApplianceTemplateCatalog.class);
        when(catalog.groupedByCategory("es")).thenReturn(Map.of());
        KitCapacityEstimator estimator = new KitCapacityEstimator(catalog);

        // no battery -> nothing sustains the kit after dark
        KitCapacityDto result = estimator.estimate(kit(1.5, 0.0, 1.0), "es");

        assertEquals(0.0, result.getNightBudgetKwh(), 0.001);
        assertEquals(7.2, result.getDayBudgetKwh(), 0.001); // 1.5 * 6.0 * 0.80
        assertEquals(7.2, result.getDaySurplusKwh(), 0.001);
    }

    @Test
    void appliancesAreLabeledWithTheirOwnDefaultHours() {
        KitCapacityEstimator estimator = estimatorWith(template("SmallLamp", 10, 5, ApplianceCategory.LIGHTING));

        KitCapacityDto result = estimator.estimate(kit(2.0, 10.0, 3.0), "es");

        assertEquals(List.of("SmallLamp (5h)"), result.getNightAppliances());
    }

    @Test
    void onlyTheBiggestFittingApplianceOfEachCategoryIsListed() {
        // night budget = 7.6 kWh -> allowance = 3.04 kWh/day; both pumps fit individually
        KitCapacityEstimator estimator = estimatorWith(
                template("PumpSmall", 400, 1, ApplianceCategory.PUMP),  // 0.4 kWh/day
                template("PumpBig", 1100, 1, ApplianceCategory.PUMP)     // 1.1 kWh/day
        );

        KitCapacityDto result = estimator.estimate(kit(2.0, 10.0, 3.0), "es");

        // only one PUMP entry, and it's the bigger one that still fits
        assertEquals(List.of("PumpBig (1h)"), result.getNightAppliances());
    }

    @Test
    void differentCategoriesEachGetTheirOwnEntryEvenWhenBothFit() {
        KitCapacityEstimator estimator = estimatorWith(
                template("SmallFridge", 100, 24, ApplianceCategory.REFRIGERATOR), // 2.4 kWh/day -> fits
                template("BigFridge", 200, 24, ApplianceCategory.REFRIGERATOR),    // 4.8 kWh/day -> too big
                template("SmallLamp", 10, 5, ApplianceCategory.LIGHTING)           // 0.05 kWh/day -> fits
        );

        KitCapacityDto result = estimator.estimate(kit(2.0, 10.0, 3.0), "es");

        assertEquals(List.of("SmallFridge (24h)", "SmallLamp (5h)"), result.getNightAppliances());
    }

    @Test
    void dayAppliancesAreCheckedAgainstTheDaySurplusNotTheFullDayBudget() {
        // day surplus = 2.0 kWh -> allowance = 2.0 * 0.40 = 0.8 kWh/day
        KitCapacityEstimator estimator = estimatorWith(
                template("SmallLamp", 10, 5, ApplianceCategory.LIGHTING),  // 0.05 kWh/day -> fits
                template("BigOven", 1000, 1, ApplianceCategory.MICROWAVE)   // 1.0 kWh/day -> too big for the surplus
        );

        KitCapacityDto result = estimator.estimate(kit(2.0, 10.0, 3.0), "es");

        assertEquals(List.of("SmallLamp (5h)"), result.getDayAppliances());
    }

    @Test
    void anApplianceCanAppearInBothListsWhenItsCheapEnoughForBothBudgets() {
        // 5W * 24h = 0.12 kWh/day easily fits both the night allowance (3.04 kWh)
        // and the day surplus allowance (0.8 kWh) - no exclusive day/night bucketing
        // by how many hours it runs, matching how a cheap always-on device (a small
        // router, for instance) genuinely works fine off either the battery at night
        // or the panels during the day.
        KitCapacityEstimator estimator = estimatorWith(template("TinyAlwaysOn", 5, 24, ApplianceCategory.ELECTRONICS));

        KitCapacityDto result = estimator.estimate(kit(2.0, 10.0, 3.0), "es");

        assertTrue(result.getNightAppliances().contains("TinyAlwaysOn (24h)"));
        assertTrue(result.getDayAppliances().contains("TinyAlwaysOn (24h)"));
    }

    @Test
    void whenPanelsDoNotOutgrowTheBatteryNeedTheDayListIsEmpty() {
        // night budget = 7.6 kWh, day budget = 1.0 * 6.0 * 0.80 = 4.8 kWh -> surplus = 0
        KitCapacityEstimator estimator = estimatorWith(template("CheapLamp", 1, 1, ApplianceCategory.LIGHTING)); // 0.001 kWh/day

        KitCapacityDto result = estimator.estimate(kit(1.0, 10.0, 3.0), "es");

        assertEquals(0.0, result.getDaySurplusKwh(), 0.001);
        assertTrue(result.getDayAppliances().isEmpty());
    }

    @Test
    void appliancesSwitchLanguageWithTheRealCatalog() throws IOException {
        KitCapacityEstimator estimator = new KitCapacityEstimator(new ApplianceTemplateCatalog(new ObjectMapper()));
        // generous kit so plenty of appliances fit in both languages
        KitDto kit = kit(5.0, 20.0, 5.0);

        KitCapacityDto esResult = estimator.estimate(kit, "es");
        KitCapacityDto enResult = estimator.estimate(kit, "en");

        assertTrue(esResult.getNightAppliances().stream().anyMatch(label -> label.startsWith("Refrigerador")));
        assertTrue(enResult.getNightAppliances().stream().anyMatch(label -> label.startsWith("Refrigerator")));
    }
}
