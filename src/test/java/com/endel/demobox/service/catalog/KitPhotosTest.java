package com.endel.demobox.service.catalog;

import com.endel.demobox.exception.BadRequestException;
import com.endel.demobox.model.catalog.Battery;
import com.endel.demobox.model.catalog.ConnectionCable;
import com.endel.demobox.model.catalog.Inverter;
import com.endel.demobox.model.catalog.InverterType;
import com.endel.demobox.model.catalog.PanelTechnology;
import com.endel.demobox.model.catalog.PhotoUrls;
import com.endel.demobox.model.catalog.Product;
import com.endel.demobox.model.catalog.SolarPanel;
import com.endel.demobox.model.dto.catalog.KitDto;
import com.endel.demobox.model.dto.catalog.KitItemDto;
import com.endel.demobox.model.dto.catalog.KitPhotoDto;
import com.endel.demobox.model.dto.catalog.SolarPanelDto;
import com.endel.demobox.repository.catalog.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class KitPhotosTest {

    @Autowired
    private KitService kitService;

    @Autowired
    private SolarPanelService panelService;

    @Autowired
    private ProductRepository productRepository;

    private static String photo(String name) {
        return "https://pub-test.r2.dev/products/" + name + ".jpg";
    }

    private <T extends Product> T save(T product, String sku, String... photos) {
        product.setSku(sku);
        product.setName(sku);
        product.setPrice(BigDecimal.TEN);
        product.setPhotos(List.of(photos));
        return productRepository.save(product);
    }

    private static KitItemDto item(Product product, int quantity) {
        KitItemDto item = new KitItemDto();
        item.setProductId(product.getId());
        item.setQuantity(quantity);
        return item;
    }

    private KitDto kit(String name, List<String> kitPhotos, KitItemDto... items) {
        KitDto dto = new KitDto();
        dto.setName(name);
        dto.setActive(true);
        dto.setImageUrls(kitPhotos);
        dto.setItems(new ArrayList<>(List.of(items)));
        return kitService.create(dto);
    }

    private SolarPanel panel() {
        SolarPanel panel = new SolarPanel();
        panel.setWattage(550);
        panel.setTechnology(PanelTechnology.MONOCRYSTALLINE);
        return panel;
    }

    private Inverter inverter() {
        Inverter inverter = new Inverter();
        inverter.setRatedPowerKw(3);
        inverter.setSurgePowerKw(6);
        inverter.setInverterType(InverterType.HYBRID);
        return inverter;
    }

    private Battery battery() {
        Battery battery = new Battery();
        battery.setVoltage(51.2);
        battery.setCapacityAh(100.0);
        return battery;
    }

    private ConnectionCable cable() {
        ConnectionCable cable = new ConnectionCable();
        cable.setLengthMeters(10);
        return cable;
    }

    @Test
    void galleryShowsKitPhotosThenEachProductInSystemOrder() {
        SolarPanel panel = save(panel(), "PH-PANEL", photo("panel-1"), photo("panel-2"));
        Inverter inverter = save(inverter(), "PH-INV", photo("inv"));
        ConnectionCable cable = save(cable(), "PH-CABLE", photo("cable"));
        Battery battery = save(battery(), "PH-BAT");

        // Items entered in a different order than they're shown.
        KitDto kit = kit("Kit Cabaña 3 kW", List.of("https://pub-test.r2.dev/kits/install.jpg"),
                item(cable, 2), item(panel, 6), item(battery, 1), item(inverter, 1));

        assertEquals("kit-cabana-3-kw", kit.getSlug());
        List<KitPhotoDto> gallery = KitService.gallery(kit);
        assertEquals(List.of("https://pub-test.r2.dev/kits/install.jpg", photo("inv"), photo("panel-1"), photo("panel-2"), photo("cable")),
                gallery.stream().map(KitPhotoDto::url).toList());
        assertEquals("Kit Cabaña 3 kW", gallery.get(0).caption());
        assertNull(gallery.get(0).productId());
        assertEquals("PH-PANEL · 6 piezas", gallery.get(2).caption());
        assertEquals(panel.getId(), gallery.get(2).productId());
        assertEquals("PH-INV · 1 pieza", gallery.get(1).caption());
        assertEquals(List.of("INVERTER", "PANEL", "BATTERY", "CABLE"),
                KitService.itemsInDisplayOrder(kit).stream().map(KitItemDto::getProductType).toList());
    }

    @Test
    void collageUsesMainPartsOnlyAndCoverPrefersTheKitsOwnPhoto() {
        SolarPanel panel = save(panel(), "COL-PANEL", photo("panel"));
        Inverter inverter = save(inverter(), "COL-INV", photo("inv"));
        Battery battery = save(battery(), "COL-BAT", photo("bat"));
        ConnectionCable cable = save(cable(), "COL-CABLE", photo("cable"));

        KitDto noPhoto = kit("Kit sin foto", List.of(), item(cable, 1), item(battery, 2), item(panel, 6), item(inverter, 1));
        assertEquals(List.of(photo("inv"), photo("panel"), photo("bat")),
                noPhoto.getCollage().stream().map(t -> t.getImageUrl()).toList());
        assertEquals(6, noPhoto.getCollage().get(1).getQuantity());
        assertEquals(photo("inv"), noPhoto.getCoverImageUrl());
        assertNull(noPhoto.getImageUrl());

        KitDto withPhoto = kit("Kit con foto", List.of("https://pub-test.r2.dev/kits/k.jpg"), item(inverter, 1));
        assertEquals("https://pub-test.r2.dev/kits/k.jpg", withPhoto.getCoverImageUrl());
    }

    @Test
    void productsKeepTheirPhotoOrderAndOlderClientsCanStillSendOneLink() {
        SolarPanelDto dto = new SolarPanelDto();
        dto.setSku("PH-DTO");
        dto.setName("Panel");
        dto.setPrice(BigDecimal.ONE);
        dto.setStockQuantity(0);
        dto.setWattage(400);
        dto.setTechnology(PanelTechnology.MONOCRYSTALLINE);
        dto.setImageUrls(List.of(photo("b"), " ", photo("a"), photo("b")));
        SolarPanelDto saved = panelService.create(dto);
        assertEquals(List.of(photo("b"), photo("a")), saved.getImageUrls());
        assertEquals(photo("b"), saved.getImageUrl());

        dto.setImageUrls(null);
        dto.setImageUrl(photo("solo"));
        SolarPanelDto legacy = panelService.update(saved.getId(), dto);
        assertEquals(List.of(photo("solo")), legacy.getImageUrls());
    }

    @Test
    void photoListRules() {
        assertThrows(BadRequestException.class, () -> PhotoUrls.resolve(List.of("javascript:alert(1)"), null));
        List<String> nine = new ArrayList<>(Collections.nCopies(0, ""));
        for (int i = 0; i < 9; i++) {
            nine.add(photo("p" + i));
        }
        BadRequestException tooMany = assertThrows(BadRequestException.class, () -> PhotoUrls.resolve(nine, null));
        assertTrue(tooMany.getMessage().contains("Máximo 8"));
        assertEquals(List.of(), PhotoUrls.resolve(null, null));
    }

    @Test
    void rowsSavedBeforePhotoListsShowTheirSingleImage() {
        SolarPanel old = panel();
        old.setSku("PH-LEGACY");
        old.setName("Legacy");
        old.setPrice(BigDecimal.ONE);
        old.setImageUrl(photo("legacy"));
        old = productRepository.save(old);
        assertEquals(List.of(photo("legacy")), productRepository.findById(old.getId()).orElseThrow().getPhotos());
    }
}
