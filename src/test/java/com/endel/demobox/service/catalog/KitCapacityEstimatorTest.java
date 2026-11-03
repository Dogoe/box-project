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

    @Test
    void estimatesDailyBudgetAsTheLimitingFactorBetweenPanelsAndBattery() {
        ApplianceTemplateCatalog catalog = mock(ApplianceTemplateCatalog.class);
        when(catalog.groupedByCategory()).thenReturn(Map.of());
        KitCapacityEstimator estimator = new KitCapacityEstimator(catalog);

        // battery-limited: 10 kWh * 0.8 * 0.95 / 1 day = 7.6 kWh; panels allow 8.0 kWh
        KitCapacityDto result = estimator.estimate(kit(2.0, 10.0, 3.0));

        assertEquals(7.6, result.getEstimatedMaxDailyKwh(), 0.001);
        assertEquals(2.73, result.getEstimatedMaxContinuousKw(), 0.01); // 3.0 / 1.10
    }

    @Test
    void panelsAloneLimitDailyBudgetWhenKitHasNoBattery() {
        ApplianceTemplateCatalog catalog = mock(ApplianceTemplateCatalog.class);
        when(catalog.groupedByCategory()).thenReturn(Map.of());
        KitCapacityEstimator estimator = new KitCapacityEstimator(catalog);

        // no battery -> daily budget should come entirely from panels (1.5 * 5.0 * 0.80)
        KitCapacityDto result = estimator.estimate(kit(1.5, 0.0, 1.0));

        assertEquals(6.0, result.getEstimatedMaxDailyKwh(), 0.001);
    }

    @Test
    void onlyListsAppliancesThatFitWithinTheCompatibleBudgetFraction() {
        ApplianceTemplateCatalog catalog = mock(ApplianceTemplateCatalog.class);
        when(catalog.groupedByCategory()).thenReturn(Map.of(
                ApplianceCategory.OTHER, List.of(
                        template("Fridge", 150, 24),  // 3.6 kWh/day -> too big
                        template("TV", 100, 4),        // 0.4 kWh/day -> fits
                        template("Lights", 10, 5),     // 0.05 kWh/day -> fits
                        template("AC", 1500, 6)        // 9.0 kWh/day -> too big
                )
        ));
        KitCapacityEstimator estimator = new KitCapacityEstimator(catalog);

        // estimatedMaxDailyKwh = 7.6 (from the first test's kit) -> budget = 7.6 * 0.40 = 3.04
        KitCapacityDto result = estimator.estimate(kit(2.0, 10.0, 3.0));

        assertEquals(List.of("Lights", "TV"), result.getCompatibleAppliances());
    }
}
