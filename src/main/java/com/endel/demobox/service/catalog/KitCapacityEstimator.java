package com.endel.demobox.service.catalog;

import com.endel.demobox.model.dto.catalog.KitCapacityDto;
import com.endel.demobox.model.dto.catalog.KitDto;
import com.endel.demobox.quote.ApplianceCategory;
import com.endel.demobox.quote.ApplianceTemplate;
import com.endel.demobox.quote.ApplianceTemplateCatalog;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Estimates what a kit can realistically power, for display on the public
 * catalog (not tied to any specific customer survey). Inverts the same
 * formulas and default assumptions SizingCalculatorService/LoadSurveyDto use
 * (6 peak sun hours, 1 day autonomy, etc.) so this stays an honest estimate
 * consistent with the quote wizard's own math, not a separate guess.
 * <p>
 * Splits the estimate into a night budget (battery only - nothing runs off
 * panels after dark) and a day budget (panel generation), with the day
 * surplus being whatever the panels generate beyond what the battery needs
 * to recharge for the next night. Each catalog appliance is checked against
 * both budgets independently, always using its own default hours/day (never
 * assumed to run for the whole night or the whole day) - a cheap appliance
 * (fridge, Starlink Mini, LED bulbs, ...) can comfortably show up in both
 * lists, exactly like running it at night off the battery or during the day
 * off the panels would both genuinely work.
 * <p>
 * Within a budget, only one appliance per category is shown (the biggest one
 * that still fits) - a kit that could power any of three pump sizes doesn't
 * need all three listed, just "yes, a pump works, up to this size."
 */
@Service
public class KitCapacityEstimator {

    private static final double DEFAULT_PEAK_SUN_HOURS = 6.0;
    private static final double DEFAULT_AUTONOMY_DAYS = 1.0;
    private static final double DEFAULT_BATTERY_ROUND_TRIP_EFFICIENCY = 0.95;
    private static final double DEFAULT_SYSTEM_DERATE_FACTOR = 0.80;
    private static final double DEFAULT_INVERTER_SAFETY_MARGIN = 1.10;

    // An appliance is only listed as "compatible" if running it (for its own
    // default hours/day) would use at most this fraction of the relevant
    // budget, leaving headroom to combine it with other devices rather than
    // consuming the whole budget by itself.
    private static final double COMPATIBLE_APPLIANCE_BUDGET_FRACTION = 0.40;

    private final ApplianceTemplateCatalog applianceTemplateCatalog;

    public KitCapacityEstimator(ApplianceTemplateCatalog applianceTemplateCatalog) {
        this.applianceTemplateCatalog = applianceTemplateCatalog;
    }

    public KitCapacityDto estimate(KitDto kit, String lang) {
        // No battery means nothing sustains the kit after dark.
        double nightBudgetKwh = kit.getUsableBatteryCapacityKwh() * DEFAULT_BATTERY_ROUND_TRIP_EFFICIENCY / DEFAULT_AUTONOMY_DAYS;
        double dayBudgetKwh = kit.getTotalPanelArrayKwp() * DEFAULT_PEAK_SUN_HOURS * DEFAULT_SYSTEM_DERATE_FACTOR;
        double daySurplusKwh = Math.max(0.0, dayBudgetKwh - nightBudgetKwh);

        double estimatedMaxContinuousKw = round(kit.getTotalInverterPowerKw() / DEFAULT_INVERTER_SAFETY_MARGIN);

        List<ApplianceTemplate> allTemplates = applianceTemplateCatalog.groupedByCategory(lang).values().stream()
                .flatMap(List::stream)
                .toList();

        List<String> nightAppliances = compatibleAppliances(allTemplates, nightBudgetKwh);
        List<String> dayAppliances = compatibleAppliances(allTemplates, daySurplusKwh);

        KitCapacityDto dto = new KitCapacityDto();
        dto.setNightBudgetKwh(round(nightBudgetKwh));
        dto.setDayBudgetKwh(round(dayBudgetKwh));
        dto.setDaySurplusKwh(round(daySurplusKwh));
        dto.setEstimatedMaxContinuousKw(estimatedMaxContinuousKw);
        dto.setNightAppliances(nightAppliances);
        dto.setDayAppliances(dayAppliances);
        return dto;
    }

    private List<String> compatibleAppliances(List<ApplianceTemplate> templates, double budgetKwh) {
        double allowance = budgetKwh * COMPATIBLE_APPLIANCE_BUDGET_FRACTION;

        Map<ApplianceCategory, ApplianceTemplate> biggestFitPerCategory = new LinkedHashMap<>();
        for (ApplianceTemplate template : templates) {
            if (dailyEnergyKwh(template) > allowance) {
                continue;
            }
            ApplianceTemplate current = biggestFitPerCategory.get(template.getCategory());
            if (current == null || dailyEnergyKwh(template) > dailyEnergyKwh(current)) {
                biggestFitPerCategory.put(template.getCategory(), template);
            }
        }

        return biggestFitPerCategory.values().stream()
                .map(this::labelWithHours)
                .sorted()
                .toList();
    }

    private String labelWithHours(ApplianceTemplate template) {
        return template.getLabel() + " (" + formatHours(template.getDefaultHoursPerDay()) + "h)";
    }

    private String formatHours(double hours) {
        return hours == Math.rint(hours) ? String.format("%.0f", hours) : String.format("%.2f", hours);
    }

    private double dailyEnergyKwh(ApplianceTemplate template) {
        return template.getWattage() * template.getDefaultHoursPerDay() / 1000.0;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
