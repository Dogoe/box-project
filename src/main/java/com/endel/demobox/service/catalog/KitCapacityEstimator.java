package com.endel.demobox.service.catalog;

import com.endel.demobox.model.dto.catalog.KitCapacityDto;
import com.endel.demobox.model.dto.catalog.KitDto;
import com.endel.demobox.quote.ApplianceTemplate;
import com.endel.demobox.quote.ApplianceTemplateCatalog;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Estimates what a kit can realistically power, for display on the public
 * catalog (not tied to any specific customer survey). Inverts the same
 * formulas and default assumptions SizingCalculatorService/LoadSurveyDto use
 * (5 peak sun hours, 1 day autonomy, etc.) so this stays an honest estimate
 * consistent with the quote wizard's own math, not a separate guess.
 */
@Service
public class KitCapacityEstimator {

    private static final double DEFAULT_PEAK_SUN_HOURS = 5.0;
    private static final double DEFAULT_AUTONOMY_DAYS = 1.0;
    private static final double DEFAULT_DEPTH_OF_DISCHARGE = 0.8;
    private static final double DEFAULT_BATTERY_ROUND_TRIP_EFFICIENCY = 0.95;
    private static final double DEFAULT_SYSTEM_DERATE_FACTOR = 0.80;
    private static final double DEFAULT_INVERTER_SAFETY_MARGIN = 1.10;

    // An appliance is only listed as "compatible" if running it alone would use at
    // most this fraction of the kit's estimated daily budget, leaving headroom to
    // combine it with other devices rather than consuming the whole kit by itself.
    private static final double COMPATIBLE_APPLIANCE_BUDGET_FRACTION = 0.40;

    private final ApplianceTemplateCatalog applianceTemplateCatalog;

    public KitCapacityEstimator(ApplianceTemplateCatalog applianceTemplateCatalog) {
        this.applianceTemplateCatalog = applianceTemplateCatalog;
    }

    public KitCapacityDto estimate(KitDto kit) {
        // No battery means this dimension doesn't constrain daily energy (there's
        // nothing to size for autonomy); the panel array becomes the only limit.
        double maxDailyFromBattery = kit.getTotalBatteryCapacityKwh() > 0
                ? kit.getTotalBatteryCapacityKwh() * DEFAULT_DEPTH_OF_DISCHARGE * DEFAULT_BATTERY_ROUND_TRIP_EFFICIENCY / DEFAULT_AUTONOMY_DAYS
                : Double.MAX_VALUE;
        double maxDailyFromPanels = kit.getTotalPanelArrayKwp() * DEFAULT_PEAK_SUN_HOURS * DEFAULT_SYSTEM_DERATE_FACTOR;
        double estimatedMaxDailyKwh = round(Math.min(maxDailyFromBattery, maxDailyFromPanels));

        double estimatedMaxContinuousKw = round(kit.getTotalInverterPowerKw() / DEFAULT_INVERTER_SAFETY_MARGIN);

        double compatibleBudgetKwh = estimatedMaxDailyKwh * COMPATIBLE_APPLIANCE_BUDGET_FRACTION;
        List<String> compatibleAppliances = applianceTemplateCatalog.groupedByCategory().values().stream()
                .flatMap(List::stream)
                .filter(template -> dailyEnergyKwh(template) <= compatibleBudgetKwh)
                .map(ApplianceTemplate::getLabel)
                .distinct()
                .sorted()
                .toList();

        KitCapacityDto dto = new KitCapacityDto();
        dto.setEstimatedMaxDailyKwh(estimatedMaxDailyKwh);
        dto.setEstimatedMaxContinuousKw(estimatedMaxContinuousKw);
        dto.setCompatibleAppliances(compatibleAppliances);
        return dto;
    }

    private double dailyEnergyKwh(ApplianceTemplate template) {
        return template.getWattage() * template.getDefaultHoursPerDay() / 1000.0;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
