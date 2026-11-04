package com.endel.demobox.controller.quote;

import com.endel.demobox.model.dto.ExchangeRateDto;
import com.endel.demobox.pdf.PdfRenderingService;
import com.endel.demobox.quote.ApplianceCategory;
import com.endel.demobox.quote.AppliancePresetResolver;
import com.endel.demobox.quote.ApplianceTemplate;
import com.endel.demobox.quote.ApplianceTemplateCatalog;
import com.endel.demobox.quote.KitRecommendationService;
import com.endel.demobox.quote.SizingCalculatorService;
import com.endel.demobox.quote.dto.ApplianceLoadDto;
import com.endel.demobox.quote.dto.KitComparisonRowDto;
import com.endel.demobox.quote.dto.LoadSurveyDto;
import com.endel.demobox.quote.dto.SizingResultDto;
import com.endel.demobox.service.ExchangeRateService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.thymeleaf.context.Context;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/quotes")
public class QuoteCalculatorController {
    private final SizingCalculatorService sizingCalculatorService;
    private final AppliancePresetResolver presetResolver;
    private final ApplianceTemplateCatalog templateCatalog;
    private final KitRecommendationService kitRecommendationService;
    private final PdfRenderingService pdfRenderingService;
    private final ExchangeRateService exchangeRateService;

    public QuoteCalculatorController(SizingCalculatorService sizingCalculatorService,
                                      AppliancePresetResolver presetResolver,
                                      ApplianceTemplateCatalog templateCatalog,
                                      KitRecommendationService kitRecommendationService,
                                      PdfRenderingService pdfRenderingService,
                                      ExchangeRateService exchangeRateService) {
        this.sizingCalculatorService = sizingCalculatorService;
        this.presetResolver = presetResolver;
        this.templateCatalog = templateCatalog;
        this.kitRecommendationService = kitRecommendationService;
        this.pdfRenderingService = pdfRenderingService;
        this.exchangeRateService = exchangeRateService;
    }

    @GetMapping("/appliance-presets")
    public Map<ApplianceCategory, List<ApplianceTemplate>> getAppliancePresets(
            @CookieValue(name = "lang", defaultValue = "es") String lang) {
        return templateCatalog.groupedByCategory(lang);
    }

    @PostMapping("/calculate")
    public SizingResultDto calculate(@Valid @RequestBody LoadSurveyDto survey,
                                      @CookieValue(name = "lang", defaultValue = "es") String lang) {
        return computeResult(survey, lang);
    }

    @PostMapping("/calculate/pdf")
    public ResponseEntity<byte[]> calculatePdf(@Valid @RequestBody LoadSurveyDto survey,
                                                @CookieValue(name = "lang", defaultValue = "es") String lang) {
        SizingResultDto result = computeResult(survey, lang);
        List<KitComparisonRowDto> comparison = result.getRecommendedKit() != null
                ? kitRecommendationService.buildComparison(result.getRecommendedKit(), result)
                : List.of();

        Context context = new Context();
        context.setVariable("survey", survey);
        context.setVariable("result", result);
        context.setVariable("comparison", comparison);
        context.setVariable("generatedAt", LocalDateTime.now());

        if (result.getRecommendedKit() != null) {
            ExchangeRateDto rate = exchangeRateService.getCurrentRate();
            BigDecimal kitPriceMxn = result.getRecommendedKit().getPriceOverride() != null
                    ? result.getRecommendedKit().getPriceOverride()
                    : result.getRecommendedKit().getComputedPrice();
            context.setVariable("kitPriceUsd", exchangeRateService.toUsd(kitPriceMxn, rate.getUsdToMxnRate()));
        }

        byte[] pdf = pdfRenderingService.render("pdf/quote", context);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"cotizacion-baja-solar-supply.pdf\"")
                .body(pdf);
    }

    private SizingResultDto computeResult(LoadSurveyDto survey, String lang) {
        List<ApplianceLoadDto> resolved = survey.getAppliances().stream()
                .map(appliance -> presetResolver.resolve(appliance, lang))
                .toList();
        survey.setAppliances(resolved);

        SizingResultDto result = sizingCalculatorService.calculate(survey);
        kitRecommendationService.findBestMatch(result).ifPresent(result::setRecommendedKit);
        return result;
    }
}
