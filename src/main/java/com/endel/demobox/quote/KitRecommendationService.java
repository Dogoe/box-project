package com.endel.demobox.quote;

import com.endel.demobox.model.dto.catalog.KitDto;
import com.endel.demobox.quote.dto.SizingResultDto;
import com.endel.demobox.service.catalog.KitService;
import org.springframework.stereotype.Service;

import java.util.Comparator;
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
}
