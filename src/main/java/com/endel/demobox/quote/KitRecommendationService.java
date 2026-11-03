package com.endel.demobox.quote;

import com.endel.demobox.model.dto.catalog.KitDto;
import com.endel.demobox.quote.dto.KitComparisonRowDto;
import com.endel.demobox.quote.dto.SizingResultDto;
import com.endel.demobox.service.catalog.KitService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Suggests the active kit whose aggregate specs best match a computed sizing
 * result. This is a first-pass heuristic, not a real recommender system: it
 * scores each dimension the target actually needs (battery kWh, panel kWp,
 * inverter kW) by relative deviation from the kit's own totals, and doubles
 * the penalty when the kit falls short so an undersized kit never outranks
 * a slightly oversized one.
 */
@Service
public class KitRecommendationService {

    private static final double UNDERSIZED_PENALTY_FACTOR = 2.0;

    private final KitService kitService;

    public KitRecommendationService(KitService kitService) {
        this.kitService = kitService;
    }

    public Optional<KitDto> findBestMatch(SizingResultDto target) {
        return kitService.findAllActive().stream()
                .filter(kit -> !kit.getItems().isEmpty())
                .min(Comparator.comparingDouble(kit -> score(kit, target)));
    }

    private double score(KitDto kit, SizingResultDto target) {
        return dimensionScore(kit.getTotalBatteryCapacityKwh(), target.getRecommendedBatteryCapacityKwh())
                + dimensionScore(kit.getTotalPanelArrayKwp(), target.getRecommendedPanelArrayKwp())
                + dimensionScore(kit.getTotalInverterPowerKw(), target.getRecommendedInverterKw());
    }

    private double dimensionScore(double kitValue, double targetValue) {
        if (targetValue <= 0) {
            return 0; // this dimension isn't needed (e.g. battery for an ON_GRID system)
        }
        double deviation = Math.abs(kitValue - targetValue) / targetValue;
        return kitValue < targetValue ? deviation * UNDERSIZED_PENALTY_FACTOR : deviation;
    }

    /**
     * Per-dimension kit-vs-target breakdown (met/short and by how much), for display
     * alongside a suggested kit. Mirrors the dimensions {@link #score} considers, and
     * the same "dimension not needed" skip when the target value is zero.
     */
    public List<KitComparisonRowDto> buildComparison(KitDto kit, SizingResultDto target) {
        List<KitComparisonRowDto> rows = new ArrayList<>();
        addRow(rows, "Arreglo de paneles", "kWp", kit.getTotalPanelArrayKwp(), target.getRecommendedPanelArrayKwp());
        addRow(rows, "Capacidad de batería", "kWh", kit.getTotalBatteryCapacityKwh(), target.getRecommendedBatteryCapacityKwh());
        addRow(rows, "Inversor", "kW", kit.getTotalInverterPowerKw(), target.getRecommendedInverterKw());
        return rows;
    }

    private void addRow(List<KitComparisonRowDto> rows, String label, String unit, double kitValue, double targetValue) {
        if (targetValue <= 0) {
            return;
        }
        boolean meets = kitValue >= targetValue;
        double diff = Math.abs(kitValue - targetValue);
        rows.add(new KitComparisonRowDto(label, unit, kitValue, targetValue, meets, diff));
    }
}
