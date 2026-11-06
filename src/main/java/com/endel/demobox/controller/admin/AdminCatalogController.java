package com.endel.demobox.controller.admin;

import com.endel.demobox.model.dto.catalog.transfer.CatalogImportResultDto;
import com.endel.demobox.service.catalog.CatalogTransferService;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** Catalog file for moving products and kits between stores (admin only, see /api/admin/** in JwtConfig). */
@RestController
@RequestMapping("/api/admin/catalog")
public class AdminCatalogController {

    private final CatalogTransferService transferService;

    public AdminCatalogController(CatalogTransferService transferService) {
        this.transferService = transferService;
    }

    @GetMapping("/export")
    public ResponseEntity<ObjectNode> export() {
        String filename = "catalogo-" + LocalDate.now() + ".json";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(transferService.exportCatalog());
    }

    /**
     * The file's text as the body. With {@code dryRun=true} (the default) nothing is saved: the
     * result says what an import would create, update or reject.
     */
    @PostMapping(value = "/import", consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_PLAIN_VALUE})
    public CatalogImportResultDto importCatalog(@RequestBody(required = false) String json,
                                                @RequestParam(defaultValue = "true") boolean dryRun) {
        return transferService.importCatalog(json, dryRun);
    }
}
