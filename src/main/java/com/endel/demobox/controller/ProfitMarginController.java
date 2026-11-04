package com.endel.demobox.controller;

import com.endel.demobox.model.dto.ProfitMarginDto;
import com.endel.demobox.service.ProfitMarginService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin-only: unlike the exchange rate, the margin isn't needed by any public page. */
@RestController
@RequestMapping("/api/settings/profit-margin")
public class ProfitMarginController {

    private final ProfitMarginService profitMarginService;

    public ProfitMarginController(ProfitMarginService profitMarginService) {
        this.profitMarginService = profitMarginService;
    }

    @GetMapping
    public ProfitMarginDto get() {
        return profitMarginService.getCurrentMargin();
    }

    @PutMapping
    public ProfitMarginDto update(@Valid @RequestBody ProfitMarginDto dto) {
        return profitMarginService.updateMargin(dto.getMarginPercent());
    }
}
