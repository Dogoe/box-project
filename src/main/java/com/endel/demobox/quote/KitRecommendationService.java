package com.endel.demobox.quote;

import com.endel.demobox.model.catalog.Battery;
import com.endel.demobox.model.catalog.Inverter;
import com.endel.demobox.model.catalog.Kit;
import com.endel.demobox.model.catalog.KitItem;
import com.endel.demobox.model.catalog.Product;
import com.endel.demobox.model.catalog.SolarPanel;
import com.endel.demobox.model.dto.catalog.KitDto;
import com.endel.demobox.quote.dto.SizingResultDto;
import com.endel.demobox.repository.catalog.KitRepository;
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

    private final KitRepository kitRepository;
    private final KitService kitService;

    public KitRecommendationService(KitRepository kitRepository, KitService kitService) {
        this.kitRepository = kitRepository;
        this.kitService = kitService;
    }

    public Optional<KitDto> findBestMatch(SizingResultDto target) {
        return kitRepository.findAll().stream()
                .filter(Kit::isActive)
                .filter(kit -> !kit.getItems().isEmpty())
                .min(Comparator.comparingDouble(kit -> score(kit, target)))
                .map(kit -> kitService.getById(kit.getId()));
    }

    private double score(Kit kit, SizingResultDto target) {
        double kitBatteryKwh = 0;
        double kitPanelKwp = 0;
        double kitInverterKw = 0;

        for (KitItem item : kit.getItems()) {
            Product product = item.getProduct();
            int quantity = item.getQuantity();
            if (product instanceof Battery battery) {
                kitBatteryKwh += battery.getCapacityKwh() * quantity;
            } else if (product instanceof SolarPanel panel) {
                kitPanelKwp += panel.getWattage() * quantity / 1000.0;
            } else if (product instanceof Inverter inverter) {
                kitInverterKw += inverter.getRatedPowerKw() * quantity;
            }
        }

        return dimensionScore(kitBatteryKwh, target.getRecommendedBatteryCapacityKwh())
                + dimensionScore(kitPanelKwp, target.getRecommendedPanelArrayKwp())
                + dimensionScore(kitInverterKw, target.getRecommendedInverterKw());
    }

    private double dimensionScore(double kitValue, double targetValue) {
        if (targetValue <= 0) {
            return 0; // this dimension isn't needed (e.g. battery for an ON_GRID system)
        }
        double deviation = Math.abs(kitValue - targetValue) / targetValue;
        return kitValue < targetValue ? deviation * UNDERSIZED_PENALTY_FACTOR : deviation;
    }
}
