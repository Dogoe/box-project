package com.endel.demobox.quote;

import com.endel.demobox.model.catalog.Battery;
import com.endel.demobox.model.catalog.BatteryChemistry;
import com.endel.demobox.model.catalog.Inverter;
import com.endel.demobox.model.catalog.InverterType;
import com.endel.demobox.model.catalog.Kit;
import com.endel.demobox.model.catalog.KitItem;
import com.endel.demobox.model.catalog.PanelTechnology;
import com.endel.demobox.model.catalog.Product;
import com.endel.demobox.model.catalog.SolarPanel;
import com.endel.demobox.model.dto.catalog.KitDto;
import com.endel.demobox.quote.dto.SizingResultDto;
import com.endel.demobox.repository.catalog.KitRepository;
import com.endel.demobox.service.catalog.KitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KitRecommendationServiceTest {

    private KitRepository kitRepository;
    private KitRecommendationService recommendationService;

    @BeforeEach
    void setUp() {
        kitRepository = mock(KitRepository.class);
        KitService kitService = mock(KitService.class);
        recommendationService = new KitRecommendationService(kitRepository, kitService);

        // Echoes back a minimal KitDto carrying the requested id, so tests can assert
        // on which kit was chosen without needing a real KitService.
        when(kitService.getById(anyLong())).thenAnswer(invocation -> {
            KitDto dto = new KitDto();
            dto.setId(invocation.getArgument(0));
            return dto;
        });
    }

    private static SolarPanel panel(double wattage) {
        SolarPanel p = new SolarPanel();
        p.setSku("PANEL-" + wattage);
        p.setName("Panel");
        p.setPrice(BigDecimal.TEN);
        p.setWattage(wattage);
        p.setTechnology(PanelTechnology.MONOCRYSTALLINE);
        return p;
    }

    private static Battery battery(double capacityKwh) {
        Battery b = new Battery();
        b.setSku("BAT-" + capacityKwh);
        b.setName("Battery");
        b.setPrice(BigDecimal.TEN);
        b.setCapacityKwh(capacityKwh);
        b.setVoltage(12);
        b.setChemistry(BatteryChemistry.LITHIUM_ION);
        return b;
    }

    private static Inverter inverter(double ratedPowerKw) {
        Inverter i = new Inverter();
        i.setSku("INV-" + ratedPowerKw);
        i.setName("Inverter");
        i.setPrice(BigDecimal.TEN);
        i.setRatedPowerKw(ratedPowerKw);
        i.setSurgePowerKw(ratedPowerKw * 2);
        i.setInverterType(InverterType.HYBRID);
        return i;
    }

    private record ItemSpec(Product product, int quantity) {
    }

    private static Kit kit(Long id, boolean active, ItemSpec... specs) {
        Kit kit = new Kit();
        kit.setId(id);
        kit.setName("Kit " + id);
        kit.setActive(active);
        for (ItemSpec spec : specs) {
            KitItem item = new KitItem();
            item.setKit(kit);
            item.setProduct(spec.product());
            item.setQuantity(spec.quantity());
            kit.getItems().add(item);
        }
        return kit;
    }

    private static SizingResultDto target(double batteryKwh, double panelKwp, double inverterKw) {
        return new SizingResultDto(0, 0, 0, inverterKw, batteryKwh, panelKwp, null);
    }

    @Test
    void picksTheKitClosestToTheTarget() {
        Kit undersized = kit(1L, true, new ItemSpec(battery(2), 1), new ItemSpec(panel(200), 1), new ItemSpec(inverter(1), 1));
        Kit closeMatch = kit(2L, true, new ItemSpec(battery(5), 1), new ItemSpec(panel(400), 3), new ItemSpec(inverter(3), 1));
        Kit oversized = kit(3L, true, new ItemSpec(battery(20), 1), new ItemSpec(panel(400), 10), new ItemSpec(inverter(10), 1));

        when(kitRepository.findAll()).thenReturn(List.of(undersized, closeMatch, oversized));

        Optional<KitDto> result = recommendationService.findBestMatch(target(5.0, 1.2, 3.0));

        assertTrue(result.isPresent());
        assertEquals(2L, result.get().getId());
    }

    @Test
    void prefersCoveringTheNeedOverBeingNumericallyCloserButUndersized() {
        // Without the undersized penalty, the 15%-short kit (raw deviation 0.15) would
        // beat the 25%-over kit (raw deviation 0.25) on battery alone. With the 2x
        // penalty applied only to the shortfall, the covering kit wins instead.
        Kit undersized = kit(1L, true, new ItemSpec(battery(4.25), 1), new ItemSpec(panel(1300), 1), new ItemSpec(inverter(1.3), 1));
        Kit covers = kit(2L, true, new ItemSpec(battery(6.25), 1), new ItemSpec(panel(1300), 1), new ItemSpec(inverter(1.3), 1));

        when(kitRepository.findAll()).thenReturn(List.of(undersized, covers));

        Optional<KitDto> result = recommendationService.findBestMatch(target(5.0, 1.3, 1.3));

        assertTrue(result.isPresent());
        assertEquals(2L, result.get().getId());
    }

    @Test
    void ignoresInactiveKits() {
        Kit inactiveButPerfect = kit(1L, false, new ItemSpec(battery(5), 1), new ItemSpec(panel(1300), 1), new ItemSpec(inverter(1.3), 1));
        Kit activeOk = kit(2L, true, new ItemSpec(battery(5), 1), new ItemSpec(panel(1300), 1), new ItemSpec(inverter(1.3), 1));

        when(kitRepository.findAll()).thenReturn(List.of(inactiveButPerfect, activeOk));

        Optional<KitDto> result = recommendationService.findBestMatch(target(5.0, 1.3, 1.3));

        assertTrue(result.isPresent());
        assertEquals(2L, result.get().getId());
    }

    @Test
    void ignoresBatteryDimensionWhenTargetNeedsNone() {
        // target battery = 0 (e.g. an ON_GRID survey) -> a kit with no battery at all
        // should not be penalized for lacking one.
        Kit noBattery = kit(1L, true, new ItemSpec(panel(1300), 1), new ItemSpec(inverter(1.3), 1));

        when(kitRepository.findAll()).thenReturn(List.of(noBattery));

        Optional<KitDto> result = recommendationService.findBestMatch(target(0.0, 1.3, 1.3));

        assertTrue(result.isPresent());
        assertEquals(1L, result.get().getId());
    }

    @Test
    void returnsEmptyWhenNoActiveKitsExist() {
        when(kitRepository.findAll()).thenReturn(List.of());

        assertTrue(recommendationService.findBestMatch(target(5.0, 1.3, 1.3)).isEmpty());
    }
}
