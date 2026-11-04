package com.endel.demobox.service.catalog;

import com.endel.demobox.exception.InvalidKitCompositionException;
import com.endel.demobox.model.catalog.AluminumStructure;
import com.endel.demobox.model.catalog.Battery;
import com.endel.demobox.model.catalog.ConnectionCable;
import com.endel.demobox.model.catalog.Kit;
import com.endel.demobox.model.catalog.KitItem;
import com.endel.demobox.model.catalog.PanelTechnology;
import com.endel.demobox.model.catalog.Product;
import com.endel.demobox.model.catalog.SolarPanel;
import com.endel.demobox.model.dto.catalog.KitDto;
import com.endel.demobox.model.dto.catalog.KitItemDto;
import com.endel.demobox.repository.catalog.KitRepository;
import com.endel.demobox.repository.catalog.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class KitServiceTest {

    @Autowired
    private KitService kitService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private KitRepository kitRepository;

    private static SolarPanel panel(String sku, BigDecimal price) {
        SolarPanel panel = new SolarPanel();
        panel.setSku(sku);
        panel.setName("Panel " + sku);
        panel.setPrice(price);
        panel.setWattage(400);
        panel.setTechnology(PanelTechnology.MONOCRYSTALLINE);
        return panel;
    }

    private static Kit kit(String name, SolarPanel panel, BigDecimal priceOverride) {
        Kit kit = new Kit();
        kit.setName(name);
        kit.setActive(true);
        kit.setPriceOverride(priceOverride);
        KitItem item = new KitItem();
        item.setKit(kit);
        item.setProduct(panel);
        item.setQuantity(1);
        kit.getItems().add(item);
        return kit;
    }

    @Test
    void findAllActive_ordersKitsFromCheapestToMostExpensive() {
        SolarPanel cheapPanel = panel("ORDER-TEST-CHEAP", BigDecimal.valueOf(100));
        SolarPanel midPanel = panel("ORDER-TEST-MID", BigDecimal.valueOf(500));
        SolarPanel expensivePanel = panel("ORDER-TEST-EXPENSIVE", BigDecimal.valueOf(900));
        productRepository.save(cheapPanel);
        productRepository.save(midPanel);
        productRepository.save(expensivePanel);

        // "Mid" kit uses a manual priceOverride cheaper than its own product price,
        // which should still be respected for ordering (not the computed sum).
        Kit expensive = kit("Z-Kit Expensive", expensivePanel, null);
        Kit cheap = kit("A-Kit Cheap", cheapPanel, null);
        Kit midOverride = kit("M-Kit Override", midPanel, BigDecimal.valueOf(50));
        kitRepository.save(expensive);
        kitRepository.save(cheap);
        kitRepository.save(midOverride);

        List<String> orderedNames = kitService.findAllActive().stream()
                .filter(k -> k.getName().startsWith("A-Kit") || k.getName().startsWith("M-Kit") || k.getName().startsWith("Z-Kit"))
                .map(KitDto::getName)
                .toList();

        assertEquals(List.of("M-Kit Override", "A-Kit Cheap", "Z-Kit Expensive"), orderedNames);
    }

    @Test
    void toDto_includesStructureAndCableItemsInComputedPriceWithSpecLabels() {
        SolarPanel panel = panel("EXTRAS-TEST-PANEL", BigDecimal.valueOf(300));

        AluminumStructure structure = new AluminumStructure();
        structure.setSku("EXTRAS-TEST-STRUCTURE");
        structure.setName("Estructura techo inclinado");
        structure.setPrice(BigDecimal.valueOf(150));

        ConnectionCable cable = new ConnectionCable();
        cable.setSku("EXTRAS-TEST-CABLE");
        cable.setName("Cable solar 10 AWG");
        cable.setPrice(BigDecimal.valueOf(50));
        cable.setLengthMeters(25);

        productRepository.save(panel);
        productRepository.save(structure);
        productRepository.save(cable);

        Kit kit = new Kit();
        kit.setName("Extras Test Kit");
        kit.setActive(true);
        addItem(kit, panel, 1);
        addItem(kit, structure, 1);
        addItem(kit, cable, 2);
        kitRepository.save(kit);

        KitDto dto = kitService.findAllActive().stream()
                .filter(k -> k.getName().equals("Extras Test Kit"))
                .findFirst()
                .orElseThrow();

        // 300 (panel) + 150 (structure) + 2 * 50 (cable) = 550
        assertEquals(BigDecimal.valueOf(550).setScale(2), dto.getComputedPrice().setScale(2));

        KitItemDto structureItem = dto.getItems().stream()
                .filter(i -> i.getProductSku().equals("EXTRAS-TEST-STRUCTURE"))
                .findFirst().orElseThrow();
        assertEquals("STRUCTURE", structureItem.getProductType());

        KitItemDto cableItem = dto.getItems().stream()
                .filter(i -> i.getProductSku().equals("EXTRAS-TEST-CABLE"))
                .findFirst().orElseThrow();
        assertEquals("CABLE", cableItem.getProductType());
        assertEquals("25 m", cableItem.getSpecLabel());
    }

    private static Battery battery(String sku, double ah) {
        Battery battery = new Battery();
        battery.setSku(sku);
        battery.setName("Bateria " + sku);
        battery.setPrice(BigDecimal.valueOf(1000));
        battery.setVoltage(12.8);
        battery.setCapacityAh(ah);
        return battery;
    }

    private static KitItemDto itemDto(Long productId, int quantity) {
        KitItemDto item = new KitItemDto();
        item.setProductId(productId);
        item.setQuantity(quantity);
        return item;
    }

    private static KitDto kitDto(String name, KitItemDto... items) {
        KitDto dto = new KitDto();
        dto.setName(name);
        dto.setActive(true);
        dto.setItems(new ArrayList<>(List.of(items)));
        return dto;
    }

    @Test
    void create_rejectsAKitWithTwoBatteryModels() {
        Battery small = productRepository.save(battery("COMP-BAT-100", 100));
        Battery big = productRepository.save(battery("COMP-BAT-200", 200));

        InvalidKitCompositionException ex = assertThrows(InvalidKitCompositionException.class, () ->
                kitService.create(kitDto("Mixed Batteries Kit", itemDto(small.getId(), 2), itemDto(big.getId(), 2))));

        assertTrue(ex.getMessage().contains("un modelo de batería"), ex.getMessage());
        assertTrue(kitService.findAll().stream().noneMatch(k -> k.getName().equals("Mixed Batteries Kit")));
    }

    @Test
    void create_mergesTheSameProductPickedInSeveralRows() {
        Battery battery = productRepository.save(battery("COMP-BAT-MERGE", 100));

        KitDto saved = kitService.create(kitDto("Merged Rows Kit", itemDto(battery.getId(), 2), itemDto(battery.getId(), 3)));

        assertEquals(1, saved.getItems().size());
        assertEquals(5, saved.getItems().get(0).getQuantity());
    }

    private static void addItem(Kit kit, Product product, int quantity) {
        KitItem item = new KitItem();
        item.setKit(kit);
        item.setProduct(product);
        item.setQuantity(quantity);
        kit.getItems().add(item);
    }
}
