package com.endel.demobox.controller;

import com.endel.demobox.exception.ResourceNotFoundException;
import com.endel.demobox.model.dto.ExchangeRateDto;
import com.endel.demobox.model.dto.catalog.KitCapacityDto;
import com.endel.demobox.model.dto.catalog.KitDto;
import com.endel.demobox.model.dto.catalog.KitPhotoDto;
import com.endel.demobox.pdf.PdfRenderingService;
import com.endel.demobox.service.ExchangeRateService;
import com.endel.demobox.service.catalog.KitCapacityEstimator;
import com.endel.demobox.service.catalog.KitService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.util.UriUtils;
import org.thymeleaf.context.Context;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
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

    @Value("${app.public-base-url}")
    private String publicBaseUrl;

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
    public String kits(Model model) {
        // A grid of kit cards; capacity and expansion are shown on each kit's own page.
        model.addAttribute("kits", kitService.findAllActive());
        return "kits";
    }

    /** Short link; sends to the canonical /kits/{id}/{slug}. */
    @GetMapping("/kits/{id:\\d+}")
    public String kitShortLink(@PathVariable Long id, Model model, HttpServletResponse response) {
        try {
            return "redirect:" + kitPath(kitService.getActiveById(id));
        } catch (ResourceNotFoundException e) {
            return kitNotFound(model, response);
        }
    }

    /** One kit with its photo gallery; a wrong or old slug redirects to the current one. */
    @GetMapping("/kits/{id:\\d+}/{slug}")
    public String kit(@PathVariable Long id, @PathVariable String slug, Model model, HttpServletResponse response,
                      @CookieValue(name = "lang", defaultValue = "es") String lang) {
        KitDto kit;
        try {
            kit = kitService.getActiveById(id);
        } catch (ResourceNotFoundException e) {
            return kitNotFound(model, response);
        }
        if (!kit.getSlug().equals(slug)) {
            return "redirect:" + kitPath(kit);
        }
        List<KitPhotoDto> gallery = KitService.gallery(kit);
        // The collage leads the gallery when the kit has no photo of its own and it shows more than one part.
        boolean collageSlide = kit.getImageUrl() == null && kit.getCollage().size() > 1;

        model.addAttribute("kit", kit);
        model.addAttribute("items", KitService.itemsInDisplayOrder(kit));
        model.addAttribute("gallery", gallery);
        model.addAttribute("collageSlide", collageSlide);
        model.addAttribute("capacity", kitCapacityEstimator.estimate(kit, lang));
        model.addAttribute("compatibility", kitService.compatibilityByKitId(List.of(id)).get(id));
        model.addAttribute("pageUrl", stripTrailingSlash(publicBaseUrl) + kitPath(kit));
        model.addAttribute("ogImage", kit.getCoverImageUrl());
        return "kit";
    }

    private static String kitPath(KitDto kit) {
        return "/kits/" + kit.getId() + "/" + UriUtils.encodePathSegment(kit.getSlug(), StandardCharsets.UTF_8);
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static String kitNotFound(Model model, HttpServletResponse response) {
        response.setStatus(HttpStatus.NOT_FOUND.value());
        model.addAttribute("kit", null);
        return "kit";
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
