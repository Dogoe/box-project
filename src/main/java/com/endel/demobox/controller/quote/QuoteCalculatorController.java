package com.endel.demobox.controller.quote;

import com.endel.demobox.quote.ApplianceCategory;
import com.endel.demobox.quote.AppliancePresetResolver;
import com.endel.demobox.quote.ApplianceTemplate;
import com.endel.demobox.quote.ApplianceTemplateCatalog;
import com.endel.demobox.quote.KitRecommendationService;
import com.endel.demobox.quote.SizingCalculatorService;
import com.endel.demobox.quote.dto.ApplianceLoadDto;
import com.endel.demobox.quote.dto.LoadSurveyDto;
import com.endel.demobox.quote.dto.SizingResultDto;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/quotes")
public class QuoteCalculatorController {
    private final SizingCalculatorService sizingCalculatorService;
    private final AppliancePresetResolver presetResolver;
    private final ApplianceTemplateCatalog templateCatalog;
    private final KitRecommendationService kitRecommendationService;

    public QuoteCalculatorController(SizingCalculatorService sizingCalculatorService,
                                      AppliancePresetResolver presetResolver,
                                      ApplianceTemplateCatalog templateCatalog,
                                      KitRecommendationService kitRecommendationService) {
        this.sizingCalculatorService = sizingCalculatorService;
        this.presetResolver = presetResolver;
        this.templateCatalog = templateCatalog;
        this.kitRecommendationService = kitRecommendationService;
    }

    @GetMapping("/appliance-presets")
    public Map<ApplianceCategory, List<ApplianceTemplate>> getAppliancePresets() {
        return templateCatalog.groupedByCategory();
    }

    @PostMapping("/calculate")
    public SizingResultDto calculate(@Valid @RequestBody LoadSurveyDto survey) {
        List<ApplianceLoadDto> resolved = survey.getAppliances().stream()
                .map(presetResolver::resolve)
                .toList();
        survey.setAppliances(resolved);

        SizingResultDto result = sizingCalculatorService.calculate(survey);
        kitRecommendationService.findBestMatch(result).ifPresent(result::setRecommendedKit);
        return result;
    }
}
