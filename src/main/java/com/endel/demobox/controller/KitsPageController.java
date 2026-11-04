package com.endel.demobox.controller;

import com.endel.demobox.model.dto.ExchangeRateDto;
import com.endel.demobox.model.dto.catalog.KitCapacityDto;
import com.endel.demobox.model.dto.catalog.KitDto;
import com.endel.demobox.pdf.PdfRenderingService;
import com.endel.demobox.service.ExchangeRateService;
import com.endel.demobox.service.catalog.KitCapacityEstimator;
import com.endel.demobox.service.catalog.KitService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.thymeleaf.context.Context;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
public class KitsPageController {

    private final KitService kitService;
    private final KitCapacityEstimator kitCapacityEstimator;
    private final PdfRenderingService pdfRenderingService;
    private final ExchangeRateService exchangeRateService;

    public KitsPageController(KitService kitService,
                               KitCapacityEstimator kitCapacityEstimator,
                               PdfRenderingService pdfRenderingService,
                               ExchangeRateService exchangeRateService) {
        this.kitService = kitService;
        this.kitCapacityEstimator = kitCapacityEstimator;
        this.pdfRenderingService = pdfRenderingService;
        this.exchangeRateService = exchangeRateService;
    }

    @GetMapping("/kits")
    public String kits(Model model, @CookieValue(name = "lang", defaultValue = "es") String lang) {
        List<KitDto> kits = kitService.findAllActive();
        Map<Long, KitCapacityDto> capacityByKitId = kits.stream()
                .collect(Collectors.toMap(KitDto::getId, kit -> kitCapacityEstimator.estimate(kit, lang)));

        model.addAttribute("kits", kits);
        model.addAttribute("capacityByKitId", capacityByKitId);
        model.addAttribute("compatibilityByKitId", kitService.compatibilityByKitId(kits.stream().map(KitDto::getId).toList()));
        return "kits";
    }

    @GetMapping("/kits/{id}/pdf")
    public ResponseEntity<byte[]> kitPdf(@PathVariable Long id,
                                          @CookieValue(name = "lang", defaultValue = "es") String lang) {
        KitDto kit = kitService.getById(id);
        KitCapacityDto capacity = kitCapacityEstimator.estimate(kit, lang);
        ExchangeRateDto rate = exchangeRateService.getCurrentRate();

        Context context = new Context();
        context.setVariable("kit", kit);
        context.setVariable("capacity", capacity);
        context.setVariable("compatibility", kitService.compatibilityByKitId(List.of(id)).get(id));
        context.setVariable("priceUsd", exchangeRateService.toUsd(kit.getSellPrice(), rate.getUsdToMxnRate()));
        context.setVariable("generatedAt", LocalDateTime.now());

        byte[] pdf = pdfRenderingService.render("pdf/kit", context);
        String filename = "kit-" + kit.getName().toLowerCase().replaceAll("[^a-z0-9]+", "-") + ".pdf";

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(pdf);
    }

    @GetMapping("/kits/pdf")
    public ResponseEntity<byte[]> kitsCatalogPdf(@CookieValue(name = "lang", defaultValue = "es") String lang) {
        List<KitDto> kits = kitService.findAllActive();
        Map<Long, KitCapacityDto> capacityByKitId = kits.stream()
                .collect(Collectors.toMap(KitDto::getId, kit -> kitCapacityEstimator.estimate(kit, lang)));
        ExchangeRateDto rate = exchangeRateService.getCurrentRate();
        Map<Long, BigDecimal> usdPriceByKitId = kits.stream()
                .collect(Collectors.toMap(KitDto::getId,
                        kit -> exchangeRateService.toUsd(kit.getSellPrice(), rate.getUsdToMxnRate())));

        Context context = new Context();
        context.setVariable("kits", kits);
        context.setVariable("capacityByKitId", capacityByKitId);
        context.setVariable("usdPriceByKitId", usdPriceByKitId);
        context.setVariable("generatedAt", LocalDateTime.now());

        byte[] pdf = pdfRenderingService.render("pdf/kit-catalog", context);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"catalogo-kits-baja-solar-supply.pdf\"")
                .body(pdf);
    }
}
