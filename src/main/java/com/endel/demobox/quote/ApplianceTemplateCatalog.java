package com.endel.demobox.quote;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Loads the curated appliance presets (common fridge/TV/AC sizes, etc.) from
 * appliance-templates-{lang}.json at startup, so the quick-quote wizard can
 * offer a pick-list instead of asking for raw wattage. Each supported
 * language is its own file with the same ids/specs, just a translated label -
 * the id is what the wizard and the sizing math key off, never the label.
 */
@Component
public class ApplianceTemplateCatalog {

    private static final String DEFAULT_LANG = "es";
    private static final List<String> SUPPORTED_LANGS = List.of("es", "en");

    private final Map<String, Map<String, ApplianceTemplate>> templatesByIdByLang;
    private final Map<String, Map<ApplianceCategory, List<ApplianceTemplate>>> templatesByCategoryByLang;

    public ApplianceTemplateCatalog(ObjectMapper objectMapper) throws IOException {
        Map<String, List<ApplianceTemplate>> templatesByLang = new LinkedHashMap<>();
        for (String lang : SUPPORTED_LANGS) {
            templatesByLang.put(lang, load(objectMapper, "/appliance-templates-" + lang + ".json"));
        }

        this.templatesByIdByLang = templatesByLang.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> entry.getValue().stream()
                        .collect(Collectors.toUnmodifiableMap(ApplianceTemplate::getId, Function.identity()))));
        this.templatesByCategoryByLang = templatesByLang.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> entry.getValue().stream()
                        .collect(Collectors.groupingBy(ApplianceTemplate::getCategory))));
    }

    private static List<ApplianceTemplate> load(ObjectMapper objectMapper, String resourcePath) throws IOException {
        try (InputStream in = ApplianceTemplateCatalog.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalStateException(resourcePath + " not found on classpath");
            }
            return objectMapper.readValue(in, new TypeReference<List<ApplianceTemplate>>() {
            });
        }
    }

    private static String normalize(String lang) {
        return SUPPORTED_LANGS.contains(lang) ? lang : DEFAULT_LANG;
    }

    public Optional<ApplianceTemplate> findById(String id, String lang) {
        return Optional.ofNullable(templatesByIdByLang.get(normalize(lang)).get(id));
    }

    public Map<ApplianceCategory, List<ApplianceTemplate>> groupedByCategory(String lang) {
        return templatesByCategoryByLang.get(normalize(lang));
    }
}
