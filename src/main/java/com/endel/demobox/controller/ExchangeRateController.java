package com.endel.demobox.controller;

import com.endel.demobox.model.dto.ExchangeRateDto;
import com.endel.demobox.service.ExchangeRateService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings/exchange-rate")
public class ExchangeRateController {

    private final ExchangeRateService exchangeRateService;

    public ExchangeRateController(ExchangeRateService exchangeRateService) {
        this.exchangeRateService = exchangeRateService;
    }

    @GetMapping
    public ExchangeRateDto get() {
        return exchangeRateService.getCurrentRate();
    }

    @PutMapping
    public ExchangeRateDto update(@Valid @RequestBody ExchangeRateDto dto) {
        return exchangeRateService.updateRate(dto.getUsdToMxnRate());
    }
}
