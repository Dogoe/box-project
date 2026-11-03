package com.endel.demobox.service;

import com.endel.demobox.model.ExchangeRate;
import com.endel.demobox.model.dto.ExchangeRateDto;
import com.endel.demobox.repository.ExchangeRateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExchangeRateServiceTest {

    private ExchangeRateRepository exchangeRateRepository;
    private ExchangeRateService exchangeRateService;

    @BeforeEach
    void setUp() {
        exchangeRateRepository = mock(ExchangeRateRepository.class);
        exchangeRateService = new ExchangeRateService(exchangeRateRepository);
    }

    @Test
    void getCurrentRate_createsADefaultRateWhenNoneExists() {
        when(exchangeRateRepository.findAll()).thenReturn(List.of());
        when(exchangeRateRepository.save(any(ExchangeRate.class))).thenAnswer((invocation) -> invocation.getArgument(0));

        ExchangeRateDto dto = exchangeRateService.getCurrentRate();

        assertEquals(new BigDecimal("17.00"), dto.getUsdToMxnRate());
        verify(exchangeRateRepository).save(any(ExchangeRate.class));
    }

    @Test
    void getCurrentRate_returnsTheExistingRateWithoutCreatingANewOne() {
        ExchangeRate existing = new ExchangeRate();
        existing.setId(1L);
        existing.setUsdToMxnRate(new BigDecimal("18.50"));
        when(exchangeRateRepository.findAll()).thenReturn(List.of(existing));

        ExchangeRateDto dto = exchangeRateService.getCurrentRate();

        assertEquals(new BigDecimal("18.50"), dto.getUsdToMxnRate());
    }

    @Test
    void updateRate_persistsTheNewValueOnTheExistingRow() {
        ExchangeRate existing = new ExchangeRate();
        existing.setId(1L);
        existing.setUsdToMxnRate(new BigDecimal("17.00"));
        when(exchangeRateRepository.findAll()).thenReturn(List.of(existing));
        when(exchangeRateRepository.save(any(ExchangeRate.class))).thenAnswer((invocation) -> invocation.getArgument(0));

        ExchangeRateDto dto = exchangeRateService.updateRate(new BigDecimal("19.25"));

        assertEquals(new BigDecimal("19.25"), dto.getUsdToMxnRate());
        assertEquals(new BigDecimal("19.25"), existing.getUsdToMxnRate());
    }

    @Test
    void toUsd_convertsAndRoundsToTwoDecimals() {
        // 4099.95 / 17.50 = 234.2828... -> rounds to 234.28
        BigDecimal usd = exchangeRateService.toUsd(new BigDecimal("4099.95"), new BigDecimal("17.50"));

        assertEquals(new BigDecimal("234.28"), usd);
    }

    @Test
    void toUsd_handlesRatesThatWouldOtherwiseProduceARepeatingDecimal() {
        // 100 / 17 is a non-terminating repeating decimal; plain BigDecimal.divide()
        // without a scale would throw ArithmeticException here.
        BigDecimal usd = exchangeRateService.toUsd(new BigDecimal("100"), new BigDecimal("17"));

        assertEquals(new BigDecimal("5.88"), usd);
    }
}
