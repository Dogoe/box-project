package com.endel.demobox.service.catalog;

import com.endel.demobox.exception.BadRequestException;
import com.endel.demobox.exception.InvalidKitCompositionException;
import com.endel.demobox.model.catalog.InverterType;
import com.endel.demobox.model.dto.catalog.ChargeControllerDto;
import com.endel.demobox.model.dto.catalog.InverterDto;
import com.endel.demobox.model.dto.catalog.KitDto;
import com.endel.demobox.model.dto.catalog.KitItemDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ChargeControllerServiceTest {

    @Autowired
    private ChargeControllerService controllerService;

    @Autowired
    private InverterService inverterService;

    @Autowired
    private KitService kitService;

    private static ChargeControllerDto controllerDto(String sku, Integer... volts) {
        ChargeControllerDto dto = new ChargeControllerDto();
        dto.setSku(sku);
        dto.setName("MPPT 150/60 " + sku);
        dto.setPrice(BigDecimal.valueOf(3500));
        dto.setStockQuantity(0);
        dto.setSupportedBatteryVoltages(Set.of(volts));
        dto.setMaxChargeCurrentA(60);
        dto.setMaxInputVoltage(150);
        return dto;
    }

    private static InverterDto inverterDto(String sku, boolean builtInCharger) {
        InverterDto dto = new InverterDto();
        dto.setSku(sku);
        dto.setName("Inversor " + sku);
        dto.setPrice(BigDecimal.valueOf(8000));
        dto.setStockQuantity(0);
        dto.setRatedPowerKw(3);
        dto.setSurgePowerKw(6);
        dto.setInverterType(InverterType.OFF_GRID);
        dto.setBatteryVoltage(48.0);
        dto.setBuiltInCharger(builtInCharger);
        dto.setMaxInputVoltage(500);
        dto.setMpptMaxChargeCurrentA(80.0);
        dto.setMaxPvInputPowerW(4000.0);
        return dto;
    }

    private static KitItemDto item(Long productId, int quantity) {
        KitItemDto item = new KitItemDto();
        item.setProductId(productId);
        item.setQuantity(quantity);
        return item;
    }

    @Test
    void createAndUpdateKeepTheSupportedBatteryVoltages() {
        ChargeControllerDto created = controllerService.create(controllerDto("CC-TEST-1", 12, 24));
        assertEquals(Set.of(12, 24), created.getSupportedBatteryVoltages());

        ChargeControllerDto change = controllerDto("CC-TEST-1", 12, 24, 48);
        ChargeControllerDto updated = controllerService.update(created.getId(), change);
        assertEquals(Set.of(12, 24, 48), controllerService.getById(updated.getId()).getSupportedBatteryVoltages());
    }

    @Test
    void onlyTwelveTwentyFourAndFortyEightVoltsAreAccepted() {
        BadRequestException ex = assertThrows(BadRequestException.class, () -> controllerService.create(controllerDto("CC-TEST-BAD", 36)));
        assertTrue(ex.getMessage().contains("12, 24 y 48 V"));
    }

    @Test
    void inverterWithoutChargerDropsItsMpptData() {
        InverterDto saved = inverterService.create(inverterDto("INV-NO-CHARGER", false));

        assertFalse(saved.getBuiltInCharger());
        assertNull(saved.getMpptMaxChargeCurrentA());
        assertNull(saved.getMaxPvInputPowerW());
        assertEquals(0, saved.getMaxInputVoltage());
        assertEquals(48.0, saved.getBatteryVoltage());

        assertTrue(inverterService.create(inverterDto("INV-WITH-CHARGER", true)).getBuiltInCharger());
    }

    @Test
    void kitShowsTheControllerAndAllowsOnlyOne() {
        Long inverterId = inverterService.create(inverterDto("INV-KIT-CC", false)).getId();
        Long controllerId = controllerService.create(controllerDto("CC-KIT-1", 12, 24, 48)).getId();

        KitDto kit = new KitDto();
        kit.setName("Kit con controlador");
        kit.setActive(true);
        kit.setItems(new ArrayList<>(List.of(item(inverterId, 1), item(controllerId, 1))));
        KitItemDto controllerItem = kitService.create(kit).getItems().stream()
                .filter(i -> i.getProductId().equals(controllerId)).findFirst().orElseThrow();
        assertEquals("CHARGE_CONTROLLER", controllerItem.getProductType());
        assertEquals("60 A MPPT", controllerItem.getSpecLabel());

        kit.setName("Kit con dos controladores");
        kit.setItems(new ArrayList<>(List.of(item(inverterId, 1), item(controllerId, 2))));
        InvalidKitCompositionException ex = assertThrows(InvalidKitCompositionException.class, () -> kitService.create(kit));
        assertTrue(ex.getMessage().contains("un controlador de carga"), ex.getMessage());
    }
}
