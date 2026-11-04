package com.endel.demobox.service;

import com.endel.demobox.model.ProfitMargin;
import com.endel.demobox.model.dto.ProfitMarginDto;
import com.endel.demobox.repository.ProfitMarginRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProfitMarginServiceTest {

    private ProfitMarginRepository profitMarginRepository;
    private ProfitMarginService profitMarginService;

    @BeforeEach
    void setUp() {
        profitMarginRepository = mock(ProfitMarginRepository.class);
        profitMarginService = new ProfitMarginService(profitMarginRepository);
    }

    @Test
    void getCurrentMargin_createsADefaultMarginWhenNoneExists() {
        when(profitMarginRepository.findAll()).thenReturn(List.of());
        when(profitMarginRepository.save(any(ProfitMargin.class))).thenAnswer((invocation) -> invocation.getArgument(0));

        ProfitMarginDto dto = profitMarginService.getCurrentMargin();

        assertEquals(new BigDecimal("30.00"), dto.getMarginPercent());
        verify(profitMarginRepository).save(any(ProfitMargin.class));
    }

    @Test
    void getCurrentMargin_returnsTheExistingMarginWithoutCreatingANewOne() {
        ProfitMargin existing = new ProfitMargin();
        existing.setId(1L);
        existing.setMarginPercent(new BigDecimal("25.00"));
        when(profitMarginRepository.findAll()).thenReturn(List.of(existing));

        ProfitMarginDto dto = profitMarginService.getCurrentMargin();

        assertEquals(new BigDecimal("25.00"), dto.getMarginPercent());
    }

    @Test
    void updateMargin_persistsTheNewValueOnTheExistingRow() {
        ProfitMargin existing = new ProfitMargin();
        existing.setId(1L);
        existing.setMarginPercent(new BigDecimal("30.00"));
        when(profitMarginRepository.findAll()).thenReturn(List.of(existing));
        when(profitMarginRepository.save(any(ProfitMargin.class))).thenAnswer((invocation) -> invocation.getArgument(0));

        ProfitMarginDto dto = profitMarginService.updateMargin(new BigDecimal("40.00"));

        assertEquals(new BigDecimal("40.00"), dto.getMarginPercent());
        assertEquals(new BigDecimal("40.00"), existing.getMarginPercent());
    }

    @Test
    void applyMargin_appliesPercentageAndRoundsToTwoDecimals() {
        // 1000 * 1.30 = 1300.00
        BigDecimal sellPrice = profitMarginService.applyMargin(new BigDecimal("1000"), new BigDecimal("30"));

        assertEquals(new BigDecimal("1300.00"), sellPrice);
    }

    @Test
    void applyMargin_handlesRatesThatWouldOtherwiseProduceARepeatingDecimal() {
        // 100 * (1 + 1/3) is a non-terminating repeating decimal; plain BigDecimal.divide()
        // without a scale would throw ArithmeticException here.
        BigDecimal sellPrice = profitMarginService.applyMargin(new BigDecimal("100"), new BigDecimal("33.33"));

        assertEquals(new BigDecimal("133.33"), sellPrice);
    }

    @Test
    void applyMargin_zeroMarginReturnsCostUnchangedButRescaled() {
        BigDecimal sellPrice = profitMarginService.applyMargin(new BigDecimal("1000"), BigDecimal.ZERO);

        assertEquals(new BigDecimal("1000.00"), sellPrice);
    }
}
