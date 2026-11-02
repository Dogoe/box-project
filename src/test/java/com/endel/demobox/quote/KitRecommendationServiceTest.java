package com.endel.demobox.quote;

import com.endel.demobox.model.dto.catalog.KitDto;
import com.endel.demobox.model.dto.catalog.KitItemDto;
import com.endel.demobox.quote.dto.SizingResultDto;
import com.endel.demobox.service.catalog.KitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KitRecommendationServiceTest {

    private KitService kitService;
    private KitRecommendationService recommendationService;

    @BeforeEach
    void setUp() {
        kitService = mock(KitService.class);
        recommendationService = new KitRecommendationService(kitService);
    }

    private static KitDto kit(Long id, double batteryKwh, double panelKwp, double inverterKw) {
        KitDto dto = new KitDto();
        dto.setId(id);
        dto.setName("Kit " + id);
        dto.setActive(true);
        dto.setTotalBatteryCapacityKwh(batteryKwh);
        dto.setTotalPanelArrayKwp(panelKwp);
        dto.setTotalInverterPowerKw(inverterKw);
        // findAllActive() already excludes inactive kits and the service only checks
        // items isn't empty, so a single placeholder item is enough here.
        dto.setItems(List.of(new KitItemDto()));
        return dto;
    }

    private static SizingResultDto target(double batteryKwh, double panelKwp, double inverterKw) {
        return new SizingResultDto(0, 0, 0, inverterKw, batteryKwh, panelKwp, null);
    }

    @Test
    void picksTheKitClosestToTheTarget() {
        KitDto undersized = kit(1L, 2, 0.2, 1);
        KitDto closeMatch = kit(2L, 5, 1.2, 3);
        KitDto oversized = kit(3L, 20, 4.0, 10);

        when(kitService.findAllActive()).thenReturn(List.of(undersized, closeMatch, oversized));

        Optional<KitDto> result = recommendationService.findBestMatch(target(5.0, 1.2, 3.0));

        assertTrue(result.isPresent());
        assertEquals(2L, result.get().getId());
    }

    @Test
    void prefersCoveringTheNeedOverBeingNumericallyCloserButUndersized() {
        // Without the undersized penalty, the 15%-short kit (raw deviation 0.15) would
        // beat the 25%-over kit (raw deviation 0.25) on battery alone. With the 2x
        // penalty applied only to the shortfall, the covering kit wins instead.
        KitDto undersized = kit(1L, 4.25, 1.3, 1.3);
        KitDto covers = kit(2L, 6.25, 1.3, 1.3);

        when(kitService.findAllActive()).thenReturn(List.of(undersized, covers));

        Optional<KitDto> result = recommendationService.findBestMatch(target(5.0, 1.3, 1.3));

        assertTrue(result.isPresent());
        assertEquals(2L, result.get().getId());
    }

    @Test
    void skipsKitsWithNoItems() {
        KitDto empty = kit(1L, 5, 1.3, 1.3);
        empty.setItems(List.of());
        KitDto withItems = kit(2L, 5, 1.3, 1.3);

        when(kitService.findAllActive()).thenReturn(List.of(empty, withItems));

        Optional<KitDto> result = recommendationService.findBestMatch(target(5.0, 1.3, 1.3));

        assertTrue(result.isPresent());
        assertEquals(2L, result.get().getId());
    }

    @Test
    void ignoresBatteryDimensionWhenTargetNeedsNone() {
        // target battery = 0 (e.g. an ON_GRID survey) -> a kit with no battery at all
        // should not be penalized for lacking one.
        KitDto noBattery = kit(1L, 0, 1.3, 1.3);

        when(kitService.findAllActive()).thenReturn(List.of(noBattery));

        Optional<KitDto> result = recommendationService.findBestMatch(target(0.0, 1.3, 1.3));

        assertTrue(result.isPresent());
        assertEquals(1L, result.get().getId());
    }

    @Test
    void returnsEmptyWhenNoActiveKitsExist() {
        when(kitService.findAllActive()).thenReturn(List.of());

        assertTrue(recommendationService.findBestMatch(target(5.0, 1.3, 1.3)).isEmpty());
    }
}
