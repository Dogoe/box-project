package com.endel.demobox.controller;

import com.endel.demobox.model.catalog.Battery;
import com.endel.demobox.model.catalog.BatteryChemistry;
import com.endel.demobox.model.catalog.Inverter;
import com.endel.demobox.model.catalog.InverterType;
import com.endel.demobox.model.catalog.Kit;
import com.endel.demobox.model.catalog.KitItem;
import com.endel.demobox.model.catalog.PanelTechnology;
import com.endel.demobox.model.catalog.SolarPanel;
import com.endel.demobox.repository.catalog.KitRepository;
import com.endel.demobox.repository.catalog.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Transactional
class KitsPageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private KitRepository kitRepository;

    private Kit kit;

    @BeforeEach
    void setUp() {
        SolarPanel panel = new SolarPanel();
        panel.setSku("PDF-PANEL-1");
        panel.setName("Panel PDF Test 400W");
        panel.setPrice(BigDecimal.valueOf(150));
        panel.setWattage(400);
        panel.setTechnology(PanelTechnology.MONOCRYSTALLINE);
        productRepository.save(panel);

        Battery battery = new Battery();
        battery.setSku("PDF-BATTERY-1");
        battery.setName("Bateria PDF Test 5kWh");
        battery.setPrice(BigDecimal.valueOf(900));
        battery.setCapacityKwh(5);
        battery.setVoltage(48);
        battery.setChemistry(BatteryChemistry.LITHIUM_ION);
        productRepository.save(battery);

        Inverter inverter = new Inverter();
        inverter.setSku("PDF-INVERTER-1");
        inverter.setName("Inversor PDF Test 3kW");
        inverter.setPrice(BigDecimal.valueOf(600));
        inverter.setRatedPowerKw(3);
        inverter.setSurgePowerKw(6);
        inverter.setInverterType(InverterType.OFF_GRID);
        productRepository.save(inverter);

        kit = new Kit();
        kit.setName("Kit PDF Test");
        kit.setDescription("Kit usado para verificar la generacion de PDF.");
        kit.setActive(true);
        kit.getItems().add(kitItem(kit, panel, 2));
        kit.getItems().add(kitItem(kit, battery, 1));
        kit.getItems().add(kitItem(kit, inverter, 1));
        kitRepository.save(kit);
    }

    private static KitItem kitItem(Kit kit, com.endel.demobox.model.catalog.Product product, int quantity) {
        KitItem item = new KitItem();
        item.setKit(kit);
        item.setProduct(product);
        item.setQuantity(quantity);
        return item;
    }

    @Test
    void kitPdf_returnsAPdfDocumentForAnExistingKit() throws Exception {
        MvcResult result = mockMvc.perform(get("/kits/" + kit.getId() + "/pdf"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().exists("Content-Disposition"))
                .andReturn();

        assertPdf(result);
    }

    @Test
    void kitPdf_returns404ForAnUnknownKit() throws Exception {
        mockMvc.perform(get("/kits/999999/pdf"))
                .andExpect(status().isNotFound());
    }

    @Test
    void kitsCatalogPdf_returnsAPdfDocumentListingActiveKits() throws Exception {
        MvcResult result = mockMvc.perform(get("/kits/pdf"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andReturn();

        assertPdf(result);
    }

    private static void assertPdf(MvcResult result) throws Exception {
        byte[] bytes = result.getResponse().getContentAsByteArray();
        assertTrue(bytes.length > 0);
        assertTrue(new String(bytes, 0, 4, StandardCharsets.US_ASCII).equals("%PDF"));
    }
}
