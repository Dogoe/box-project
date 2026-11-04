package com.endel.demobox.service.catalog;

import com.endel.demobox.model.dto.catalog.KitCapacityDto;
import com.endel.demobox.model.dto.catalog.KitDto;
import com.endel.demobox.quote.ApplianceCategory;
import com.endel.demobox.quote.ApplianceTemplate;
import com.endel.demobox.quote.ApplianceTemplateCatalog;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KitCapacityEstimatorTest {

    private static ApplianceTemplate template(String id, double wattage, double hours) {
        ApplianceTemplate template = new ApplianceTemplate();
        template.setId(id);
        template.setCategory(ApplianceCategory.OTHER);
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
        dto.setTotalInverterPowerKw(inverterKw);
        return dto;
    }

    private static KitCapacityEstimator estimatorWith(ApplianceTemplate... templates) {
        ApplianceTemplateCatalog catalog = mock(ApplianceTemplateCatalog.class);
        when(catalog.groupedByCategory()).thenReturn(Map.of(ApplianceCategory.OTHER, List.of(templates)));
        return new KitCapacityEstimator(catalog);
    }

    @Test
    void nightAndDayBudgetsAreComputedIndependentlyInsteadOfTakingTheMinimum() {
        ApplianceTemplateCatalog catalog = mock(ApplianceTemplateCatalog.class);
        when(catalog.groupedByCategory()).thenReturn(Map.of());
        KitCapacityEstimator estimator = new KitCapacityEstimator(catalog);

        // night (battery): 10 kWh * 0.8 * 0.95 = 7.6 kWh
        // day (panels): 2.0 kWp * 5.0 peak sun hours * 0.80 = 8.0 kWh
        // surplus: 8.0 - 7.6 = 0.4 kWh
        KitCapacityDto result = estimator.estimate(kit(2.0, 10.0, 3.0));

        assertEquals(7.6, result.getNightBudgetKwh(), 0.001);
        assertEquals(8.0, result.getDayBudgetKwh(), 0.001);
        assertEquals(0.4, result.getDaySurplusKwh(), 0.001);
        assertEquals(2.73, result.getEstimatedMaxContinuousKw(), 0.01); // 3.0 / 1.10
    }

    @Test
    void noBatteryMeansNoNightBudgetAndTheFullDayGenerationIsSurplus() {
        ApplianceTemplateCatalog catalog = mock(ApplianceTemplateCatalog.class);
        when(catalog.groupedByCategory()).thenReturn(Map.of());
        KitCapacityEstimator estimator = new KitCapacityEstimator(catalog);

        // no battery -> nothing sustains the kit after dark
        KitCapacityDto result = estimator.estimate(kit(1.5, 0.0, 1.0));

        assertEquals(0.0, result.getNightBudgetKwh(), 0.001);
        assertEquals(6.0, result.getDayBudgetKwh(), 0.001); // 1.5 * 5.0 * 0.80
        assertEquals(6.0, result.getDaySurplusKwh(), 0.001);
    }

    @Test
    void alwaysOnAppliancesAreCheckedAgainstTheNightBudget() {
        // night budget = 7.6 kWh -> always-on budget = 7.6 * 0.40 = 3.04 kWh/day
        KitCapacityEstimator estimator = estimatorWith(
                template("SmallFridge", 100, 24), // 2.4 kWh/day -> fits
                template("BigFridge", 200, 24)     // 4.8 kWh/day -> too big
        );

        KitCapacityDto result = estimator.estimate(kit(2.0, 10.0, 3.0));

        assertEquals(List.of("SmallFridge"), result.getAlwaysOnAppliances());
    }

    @Test
    void extraDaytimeAppliancesAreCheckedAgainstTheDaySurplusNotTheFullDayBudget() {
        // day surplus = 0.4 kWh -> daytime budget = 0.4 * 0.40 = 0.16 kWh/day
        KitCapacityEstimator estimator = estimatorWith(
                template("SmallLamp", 10, 5),  // 0.05 kWh/day -> fits
                template("TV", 100, 4)          // 0.4 kWh/day -> too big for the surplus
        );

        KitCapacityDto result = estimator.estimate(kit(2.0, 10.0, 3.0));

        assertEquals(List.of("SmallLamp"), result.getExtraDaytimeAppliances());
    }

    @Test
    void anAlwaysOnApplianceNeverAppearsInTheExtraDaytimeListEvenIfCheapEnoughToFitThere() {
        // 5W * 24h = 0.12 kWh/day would fit the 0.16 kWh daytime budget, but it runs
        // around the clock, so it must only be evaluated against the night budget.
        KitCapacityEstimator estimator = estimatorWith(template("TinyAlwaysOn", 5, 24));

        KitCapacityDto result = estimator.estimate(kit(2.0, 10.0, 3.0));

        assertTrue(result.getAlwaysOnAppliances().contains("TinyAlwaysOn"));
        assertTrue(result.getExtraDaytimeAppliances().isEmpty());
    }

    @Test
    void whenPanelsDoNotOutgrowTheBatteryNeedTheExtraDaytimeListIsEmpty() {
        // night budget = 7.6 kWh, day budget = 1.0 * 5.0 * 0.80 = 4.0 kWh -> surplus = 0
        KitCapacityEstimator estimator = estimatorWith(template("CheapLamp", 1, 1)); // 0.001 kWh/day

        KitCapacityDto result = estimator.estimate(kit(1.0, 10.0, 3.0));

        assertEquals(0.0, result.getDaySurplusKwh(), 0.001);
        assertTrue(result.getExtraDaytimeAppliances().isEmpty());
    }
}
