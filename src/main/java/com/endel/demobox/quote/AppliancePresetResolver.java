package com.endel.demobox.quote;

import com.endel.demobox.exception.InvalidApplianceTemplateException;
import com.endel.demobox.quote.dto.ApplianceLoadDto;
import org.springframework.stereotype.Component;

/**
 * Expands a preset-based appliance selection (templateId + quantity) into a
 * fully specified ApplianceLoadDto, so SizingCalculatorService never has to
 * know presets exist. Custom entries (no templateId) pass through unchanged.
 */
@Component
public class AppliancePresetResolver {

    private final ApplianceTemplateCatalog catalog;

    public AppliancePresetResolver(ApplianceTemplateCatalog catalog) {
        this.catalog = catalog;
    }

    public ApplianceLoadDto resolve(ApplianceLoadDto input) {
        if (input.getTemplateId() == null || input.getTemplateId().isBlank()) {
            return input;
        }

        ApplianceTemplate template = catalog.findById(input.getTemplateId())
                .orElseThrow(() -> new InvalidApplianceTemplateException(
                        "Unknown appliance template: " + input.getTemplateId()));

        ApplianceLoadDto resolved = new ApplianceLoadDto();
        resolved.setName(template.getLabel());
        resolved.setWatts(template.getWattage());
        resolved.setHoursPerDay(input.getHoursPerDayOverride() != null
                ? input.getHoursPerDayOverride()
                : template.getDefaultHoursPerDay());
        resolved.setQuantity(input.getQuantity());
        resolved.setRunsSimultaneously(template.isDefaultRunsSimultaneously());
        resolved.setMotorLoad(template.isMotorLoad());
        resolved.setSurgeMultiplier(template.getSurgeMultiplier());
        return resolved;
    }
}
