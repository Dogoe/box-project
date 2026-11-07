package com.endel.demobox.controller.admin;

import com.endel.demobox.model.catalog.Kit;
import com.endel.demobox.repository.catalog.KitRepository;
import com.endel.demobox.repository.catalog.ProductRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminKitDiagramControllerTest {

    private static final String CATALOG = """
            {
              "format": "baja-solar-catalog",
              "version": 1,
              "products": [
                { "type": "SOLAR_PANEL", "sku": "T-DG-PNL", "name": "Panel 100 W", "price": 900,
                  "wattage": 100, "technology": "MONOCRYSTALLINE", "efficiencyPercent": 20, "voc": 22, "isc": 6.5 },
                { "type": "BATTERY", "sku": "T-DG-BAT", "name": "Batería 12.8 V 100 Ah", "price": 4000,
                  "capacityAh": 100, "voltage": 12.8, "chemistry": "LIFEPO4" },
                { "type": "INVERTER", "sku": "T-DG-INV", "name": "Inversor 600 W 12 V", "price": 1500,
                  "ratedPowerKw": 0.6, "surgePowerKw": 1.2, "inverterType": "OFF_GRID", "builtInCharger": false, "batteryVoltage": 12 },
                { "type": "CHARGE_CONTROLLER", "sku": "T-DG-CTL", "name": "Controlador PWM 10 A", "price": 300,
                  "supportedBatteryVoltages": [12, 24], "maxChargeCurrentA": 10, "maxInputVoltage": 50 },
                { "type": "PROTECTION", "sku": "T-DG-ANL", "name": "Fusible ANL 80 A", "price": 150,
                  "protectionType": "FUSE", "fuseClass": "ANL", "ratedCurrentA": 80, "maxVoltage": 32 }
              ],
              "kits": [
                { "name": "TEST Diagrama 12 V <b>",
                  "items": [ { "sku": "T-DG-PNL", "quantity": 1 }, { "sku": "T-DG-BAT", "quantity": 1 },
                             { "sku": "T-DG-INV", "quantity": 1 }, { "sku": "T-DG-CTL", "quantity": 1 },
                             { "sku": "T-DG-ANL", "quantity": 1 } ] }
              ]
            }
            """;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private KitRepository kitRepository;

    @AfterEach
    void removeTestData() {
        kitRepository.findAll().stream().filter(kit -> kit.getName().startsWith("TEST ")).forEach(kitRepository::delete);
        productRepository.findAll().stream().filter(product -> product.getSku().startsWith("T-DG-")).forEach(productRepository::delete);
    }

    private String adminToken() throws Exception {
        String body = mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@bajasolarsupply.com\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return (String) objectMapper.readValue(body, Map.class).get("token");
    }

    private Long importKit(String token) throws Exception {
        mockMvc.perform(post("/api/admin/catalog/import").param("dryRun", "false").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(CATALOG))
                .andExpect(status().isOk());
        return kitRepository.findAll().stream().filter(k -> k.getName().startsWith("TEST Diagrama")).map(Kit::getId).findFirst().orElseThrow();
    }

    @Test
    void adminGetsTheDrawingAndTables() throws Exception {
        String token = adminToken();
        Long id = importKit(token);

        mockMvc.perform(get("/api/admin/kits/" + id + "/diagram").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(allOf(
                        containsString("<svg id=\"kit-diagram-svg\""),
                        // the kit name is escaped, not injected as markup
                        containsString("TEST Diagrama 12 V &lt;b&gt;"),
                        not(containsString("TEST Diagrama 12 V <b>")),
                        containsString("Controlador de carga 10 A"),
                        containsString("Fusible ANL 80 A · 32 V"),
                        containsString("FALTA"),
                        containsString("Orden para conectar"))));
    }

    @Test
    void diagramNeedsAnAdmin() throws Exception {
        mockMvc.perform(get("/api/admin/kits/1/diagram")).andExpect(status().is4xxClientError());
    }

    @Test
    void unknownKitIs404() throws Exception {
        mockMvc.perform(get("/api/admin/kits/99999999/diagram").header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    void pageShellIsServedAndLoadsTheScript() throws Exception {
        mockMvc.perform(get("/admin/kits/5/diagrama"))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(containsString("data-kit-id=\"5\""), containsString("/js/kit-diagram.js"))));
    }
}
