package com.endel.demobox.quote;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Loads the curated appliance presets (common fridge/TV/AC sizes, etc.) from
 * appliance-templates.json at startup, so the quick-quote wizard can offer a
 * pick-list instead of asking for raw wattage.
 */
@Component
public class ApplianceTemplateCatalog {

    private final Map<String, ApplianceTemplate> templatesById;
    private final Map<ApplianceCategory, List<ApplianceTemplate>> templatesByCategory;

    public ApplianceTemplateCatalog(ObjectMapper objectMapper) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/appliance-templates.json")) {
            if (in == null) {
                throw new IllegalStateException("appliance-templates.json not found on classpath");
            }
            List<ApplianceTemplate> templates = objectMapper.readValue(in, new TypeReference<List<ApplianceTemplate>>() {
            });
            this.templatesById = templates.stream()
                    .collect(Collectors.toUnmodifiableMap(ApplianceTemplate::getId, Function.identity()));
            this.templatesByCategory = templates.stream()
                    .collect(Collectors.groupingBy(ApplianceTemplate::getCategory));
        }
    }

    public Optional<ApplianceTemplate> findById(String id) {
        return Optional.ofNullable(templatesById.get(id));
    }

    public Map<ApplianceCategory, List<ApplianceTemplate>> groupedByCategory() {
        return templatesByCategory;
    }
}
