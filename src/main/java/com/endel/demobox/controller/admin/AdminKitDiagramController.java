package com.endel.demobox.controller.admin;

import com.endel.demobox.service.catalog.KitDiagramService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * The kit's wiring diagram for the installer, as an HTML fragment (SVG drawing + reference tables).
 * Admin only (/api/admin/**); the page /admin/kits/{id}/diagrama fetches it with the admin's token.
 */
@RestController
public class AdminKitDiagramController {

    private static final Locale SPANISH = Locale.forLanguageTag("es-MX");

    private final KitDiagramService diagramService;
    private final SpringTemplateEngine templateEngine;

    public AdminKitDiagramController(KitDiagramService diagramService, SpringTemplateEngine templateEngine) {
        this.diagramService = diagramService;
        this.templateEngine = templateEngine;
    }

    // No "produces": that would also apply to the JSON error body of a missing kit.
    @GetMapping("/api/admin/kits/{id}/diagram")
    public ResponseEntity<String> diagram(@PathVariable Long id) {
        Context context = new Context(SPANISH);
        context.setVariable("d", diagramService.build(id));
        return ResponseEntity.ok()
                .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                .body(templateEngine.process("admin/kit-diagram-content", context));
    }
}
