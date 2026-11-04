package com.endel.demobox.service;

import com.endel.demobox.model.ProfitMargin;
import com.endel.demobox.model.dto.ProfitMarginDto;
import com.endel.demobox.repository.ProfitMarginRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class ProfitMarginService {

    private static final BigDecimal DEFAULT_MARGIN_PERCENT = new BigDecimal("30.00");
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private final ProfitMarginRepository profitMarginRepository;

    public ProfitMarginService(ProfitMarginRepository profitMarginRepository) {
        this.profitMarginRepository = profitMarginRepository;
    }

    public ProfitMarginDto getCurrentMargin() {
        return toDto(findOrCreate());
    }

    public ProfitMarginDto updateMargin(BigDecimal newPercent) {
        ProfitMargin margin = findOrCreate();
        margin.setMarginPercent(newPercent);
        return toDto(profitMarginRepository.save(margin));
    }

    /** Applies the given markup percentage on top of a cost, rounded to 2 decimals. */
    public BigDecimal applyMargin(BigDecimal cost, BigDecimal marginPercent) {
        BigDecimal multiplier = BigDecimal.ONE.add(marginPercent.divide(ONE_HUNDRED, 4, RoundingMode.HALF_UP));
        return cost.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
    }

    private ProfitMargin findOrCreate() {
        return profitMarginRepository.findAll().stream()
                .findFirst()
                .orElseGet(() -> {
                    ProfitMargin margin = new ProfitMargin();
                    margin.setMarginPercent(DEFAULT_MARGIN_PERCENT);
                    return profitMarginRepository.save(margin);
                });
    }

    private ProfitMarginDto toDto(ProfitMargin margin) {
        ProfitMarginDto dto = new ProfitMarginDto();
        dto.setMarginPercent(margin.getMarginPercent());
        dto.setUpdatedAt(margin.getUpdatedAt());
        return dto;
    }
}
