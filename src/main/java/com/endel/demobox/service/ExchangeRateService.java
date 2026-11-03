package com.endel.demobox.service;

import com.endel.demobox.model.ExchangeRate;
import com.endel.demobox.model.dto.ExchangeRateDto;
import com.endel.demobox.repository.ExchangeRateRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class ExchangeRateService {

    private static final BigDecimal DEFAULT_USD_TO_MXN_RATE = new BigDecimal("17.00");

    private final ExchangeRateRepository exchangeRateRepository;

    public ExchangeRateService(ExchangeRateRepository exchangeRateRepository) {
        this.exchangeRateRepository = exchangeRateRepository;
    }

    public ExchangeRateDto getCurrentRate() {
        return toDto(findOrCreate());
    }

    public ExchangeRateDto updateRate(BigDecimal newRate) {
        ExchangeRate rate = findOrCreate();
        rate.setUsdToMxnRate(newRate);
        return toDto(exchangeRateRepository.save(rate));
    }

    /** Converts a price stored in MXN to its USD equivalent at the given rate (MXN per 1 USD). */
    public BigDecimal toUsd(BigDecimal mxnAmount, BigDecimal usdToMxnRate) {
        return mxnAmount.divide(usdToMxnRate, 2, RoundingMode.HALF_UP);
    }

    private ExchangeRate findOrCreate() {
        return exchangeRateRepository.findAll().stream()
                .findFirst()
                .orElseGet(() -> {
                    ExchangeRate rate = new ExchangeRate();
                    rate.setUsdToMxnRate(DEFAULT_USD_TO_MXN_RATE);
                    return exchangeRateRepository.save(rate);
                });
    }

    private ExchangeRateDto toDto(ExchangeRate rate) {
        ExchangeRateDto dto = new ExchangeRateDto();
        dto.setUsdToMxnRate(rate.getUsdToMxnRate());
        dto.setUpdatedAt(rate.getUpdatedAt());
        return dto;
    }
}
