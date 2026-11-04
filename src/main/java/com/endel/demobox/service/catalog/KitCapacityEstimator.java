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
 * <p>
 * Splits the estimate into a night budget (battery only - nothing runs off
 * panels after dark) and a day budget (panel generation). Appliances that run
 * close to 24h/day (fridges, freezers, ...) depend on the battery lasting the
 * night, so they're checked against the night budget. Whatever the panels
 * generate beyond what the battery needs to recharge for the next night is
 * the day surplus, and daytime-only appliances are checked against that.
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
    // most this fraction of the relevant budget, leaving headroom to combine it
    // with other devices rather than consuming the whole budget by itself.
    private static final double COMPATIBLE_APPLIANCE_BUDGET_FRACTION = 0.40;

    // Appliances that run at or above this many hours/day are treated as "always
    // on" (fridges, freezers, always-on routers) rather than daytime-only.
    private static final double ALWAYS_ON_HOURS_THRESHOLD = 20.0;

    private final ApplianceTemplateCatalog applianceTemplateCatalog;

    public KitCapacityEstimator(ApplianceTemplateCatalog applianceTemplateCatalog) {
        this.applianceTemplateCatalog = applianceTemplateCatalog;
    }

    public KitCapacityDto estimate(KitDto kit) {
        // No battery means nothing sustains the kit after dark.
        double nightBudgetKwh = kit.getTotalBatteryCapacityKwh() > 0
                ? kit.getTotalBatteryCapacityKwh() * DEFAULT_DEPTH_OF_DISCHARGE * DEFAULT_BATTERY_ROUND_TRIP_EFFICIENCY / DEFAULT_AUTONOMY_DAYS
                : 0.0;
        double dayBudgetKwh = kit.getTotalPanelArrayKwp() * DEFAULT_PEAK_SUN_HOURS * DEFAULT_SYSTEM_DERATE_FACTOR;
        double daySurplusKwh = Math.max(0.0, dayBudgetKwh - nightBudgetKwh);

        double estimatedMaxContinuousKw = round(kit.getTotalInverterPowerKw() / DEFAULT_INVERTER_SAFETY_MARGIN);

        List<ApplianceTemplate> allTemplates = applianceTemplateCatalog.groupedByCategory().values().stream()
                .flatMap(List::stream)
                .toList();

        double alwaysOnBudgetKwh = Math.min(nightBudgetKwh, dayBudgetKwh) * COMPATIBLE_APPLIANCE_BUDGET_FRACTION;
        List<String> alwaysOnAppliances = allTemplates.stream()
                .filter(template -> template.getDefaultHoursPerDay() >= ALWAYS_ON_HOURS_THRESHOLD)
                .filter(template -> dailyEnergyKwh(template) <= alwaysOnBudgetKwh)
                .map(ApplianceTemplate::getLabel)
                .distinct()
                .sorted()
                .toList();

        double daytimeBudgetKwh = daySurplusKwh * COMPATIBLE_APPLIANCE_BUDGET_FRACTION;
        List<String> extraDaytimeAppliances = allTemplates.stream()
                .filter(template -> template.getDefaultHoursPerDay() < ALWAYS_ON_HOURS_THRESHOLD)
                .filter(template -> dailyEnergyKwh(template) <= daytimeBudgetKwh)
                .map(ApplianceTemplate::getLabel)
                .distinct()
                .sorted()
                .toList();

        KitCapacityDto dto = new KitCapacityDto();
        dto.setNightBudgetKwh(round(nightBudgetKwh));
        dto.setDayBudgetKwh(round(dayBudgetKwh));
        dto.setDaySurplusKwh(round(daySurplusKwh));
        dto.setEstimatedMaxContinuousKw(estimatedMaxContinuousKw);
        dto.setAlwaysOnAppliances(alwaysOnAppliances);
        dto.setExtraDaytimeAppliances(extraDaytimeAppliances);
        return dto;
    }

    private double dailyEnergyKwh(ApplianceTemplate template) {
        return template.getWattage() * template.getDefaultHoursPerDay() / 1000.0;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
