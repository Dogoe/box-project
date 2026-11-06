package com.endel.demobox.controller.admin;

import com.endel.demobox.model.catalog.Battery;
import com.endel.demobox.model.catalog.Kit;
import com.endel.demobox.repository.catalog.KitRepository;
import com.endel.demobox.repository.catalog.ProductRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Through the real HTTP stack and database. Not @Transactional on purpose: a review (dry run) must
 * really roll back, which a surrounding test transaction would hide. Test data is removed afterwards.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminCatalogControllerTest {

    private static final String CATALOG = """
            {
              "format": "baja-solar-catalog",
              "version": 1,
              "products": [
                { "type": "SOLAR_PANEL", "sku": "T-PNL-550", "name": "Panel 550 W", "brand": "Jinko", "price": 2450.00,
                  "stockQuantity": 10, "imageUrls": ["https://img.example.com/p1.jpg", "https://img.example.com/p2.jpg"],
                  "wattage": 550, "technology": "MONOCRYSTALLINE", "efficiencyPercent": 21.3, "voc": 49.6, "isc": 14 },
                { "type": "BATTERY", "sku": "T-BAT-100", "name": "Batería LiFePO4 24 V 100 Ah", "price": 8900,
                  "capacityAh": 100, "voltage": 24, "chemistry": "LIFEPO4", "cycleLife": 6000 },
                { "type": "INVERTER", "sku": "T-INV-3K", "name": "Inversor híbrido 3 kW", "price": 9800,
                  "ratedPowerKw": 3, "surgePowerKw": 6, "inverterType": "HYBRID", "builtInCharger": true,
                  "mpptChannels": 1, "maxInputVoltage": 500, "batteryVoltage": 24, "mpptMinVoltage": 60,
                  "mpptMaxChargeCurrentA": 100, "mpptMaxInputCurrentA": 22, "maxPvInputPowerW": 4000 }
              ],
              "kits": [
                { "name": "TEST Kit Cabaña", "description": "Kit de prueba", "priceOverride": 39900,
                  "items": [ { "sku": "T-PNL-550", "quantity": 4 }, { "sku": "T-BAT-100", "quantity": 1 },
                             { "sku": "T-INV-3K", "quantity": 1 } ] }
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
        productRepository.findAll().stream().filter(product -> product.getSku().startsWith("T-")).forEach(productRepository::delete);
    }

    private String adminToken() throws Exception {
        String body = mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@bajasolarsupply.com\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return (String) objectMapper.readValue(body, Map.class).get("token");
    }

    private JsonNode importFile(String json, boolean dryRun) throws Exception {
        String body = mockMvc.perform(post("/api/admin/catalog/import").param("dryRun", String.valueOf(dryRun))
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private JsonNode export() throws Exception {
        String body = mockMvc.perform(get("/api/admin/catalog/export").header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment; filename=\"catalogo-")))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    /** The test products and kits of an export, without the date, so two exports can be compared. */
    private static JsonNode testPart(JsonNode export) {
        ObjectNode part = new ObjectMapper().createObjectNode();
        ArrayNode products = part.putArray("products");
        export.get("products").forEach(p -> {
            if (p.get("sku").asText().startsWith("T-")) {
                products.add(p);
            }
        });
        ArrayNode kits = part.putArray("kits");
        export.get("kits").forEach(k -> {
            if (k.get("name").asText().startsWith("TEST ")) {
                kits.add(k);
            }
        });
        return part;
    }

    private static List<String> errorsOf(JsonNode result) {
        List<String> errors = new ArrayList<>();
        result.get("fileErrors").forEach(e -> errors.add(e.asText()));
        result.get("rows").forEach(row -> row.get("errors").forEach(e -> errors.add(row.get("key").asText() + ": " + e.asText())));
        return errors;
    }

    @Test
    void exportAndImportAreAdminOnly() throws Exception {
        mockMvc.perform(get("/api/admin/catalog/export")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/catalog/import").contentType(MediaType.APPLICATION_JSON).content(CATALOG))
                .andExpect(status().isForbidden());
    }

    @Test
    void reviewingAFileSavesNothing() throws Exception {
        JsonNode result = importFile(CATALOG, true);

        assertThat(errorsOf(result)).isEmpty();
        assertThat(result.get("saved").asBoolean()).isFalse();
        assertThat(result.at("/products/created").asInt()).isEqualTo(3);
        assertThat(result.at("/kits/created").asInt()).isEqualTo(1);
        assertThat(productRepository.findBySku("T-PNL-550")).isEmpty();
        assertThat(kitRepository.findByName("TEST Kit Cabaña")).isEmpty();
    }

    @Test
    void importsTheFileAndExportsItBackTheSame() throws Exception {
        JsonNode result = importFile(CATALOG, false);
        assertThat(errorsOf(result)).isEmpty();
        assertThat(result.get("saved").asBoolean()).isTrue();

        Kit kit = kitRepository.findByName("TEST Kit Cabaña").orElseThrow();
        assertThat(kit.getItems()).hasSize(3);
        assertThat(kit.getPriceOverride()).isEqualByComparingTo("39900");

        JsonNode exported = testPart(export());
        JsonNode panel = exported.get("products").get(0);
        assertThat(panel.get("type").asText()).isEqualTo("SOLAR_PANEL");
        assertThat(panel.has("id")).isFalse();
        assertThat(panel.get("imageUrls")).hasSize(2);
        JsonNode battery = exported.get("products").get(1);
        assertThat(battery.get("type").asText()).isEqualTo("BATTERY");
        assertThat(battery.has("capacityKwh")).as("computed values are not exported").isFalse();
        assertThat(battery.get("stockQuantity").asInt()).as("stock defaults to 0").isZero();
        assertThat(exported.at("/kits/0/items")).hasSize(3);

        // Importing the export again changes nothing.
        ObjectNode file = (ObjectNode) objectMapper.readTree(CATALOG);
        file.set("products", exported.get("products"));
        file.set("kits", exported.get("kits"));
        JsonNode again = importFile(file.toString(), false);
        assertThat(errorsOf(again)).isEmpty();
        assertThat(again.at("/products/unchanged").asInt()).isEqualTo(3);
        assertThat(again.at("/kits/unchanged").asInt()).isEqualTo(1);

        // Into an empty store (like moving to production), it recreates the same catalog.
        removeTestData();
        JsonNode moved = importFile(file.toString(), false);
        assertThat(moved.at("/products/created").asInt()).isEqualTo(3);
        assertThat(testPart(export())).isEqualTo(exported);
    }

    @Test
    void anExistingSkuIsUpdatedAndKeepsWhatTheFileLeavesOut() throws Exception {
        importFile(CATALOG, false);

        JsonNode result = importFile("""
                { "products": [ { "type": "BATTERY", "sku": "T-BAT-100", "price": 9500 } ] }
                """, false);

        assertThat(errorsOf(result)).isEmpty();
        assertThat(result.at("/rows/0/action").asText()).isEqualTo("UPDATE");
        Battery battery = (Battery) productRepository.findBySku("T-BAT-100").orElseThrow();
        assertThat(battery.getPrice()).isEqualByComparingTo(new BigDecimal("9500"));
        assertThat(battery.getName()).isEqualTo("Batería LiFePO4 24 V 100 Ah");
        assertThat(battery.getCapacityAh()).isEqualTo(100);
    }

    @Test
    void anyErrorSavesNothingAndEachProblemIsExplained() throws Exception {
        JsonNode result = importFile("""
                {
                  "products": [
                    { "type": "SOLAR_PANEL", "sku": "T-OK", "name": "Panel bueno", "price": 100, "wattage": 400,
                      "technology": "MONOCRYSTALLINE" },
                    { "type": "BATTERY", "sku": "T-NO-AH", "name": "Sin capacidad", "price": 100, "voltage": 12 },
                    { "type": "BATTERY", "sku": "T-CHEM", "name": "Química rara", "price": 100, "capacityAh": 50,
                      "voltage": 12, "chemistry": "NUCLEAR" },
                    { "type": "PANEL", "sku": "T-TYPE", "name": "Tipo mal escrito", "price": 1 },
                    { "type": "SOLAR_PANEL", "sku": "T-TYPO", "name": "Campo mal escrito", "price": 1, "wattage": 300,
                      "technology": "MONOCRYSTALLINE", "potencia": 300 },
                    { "type": "SOLAR_PANEL", "sku": "T-OK", "name": "Repetido", "price": 1, "wattage": 300,
                      "technology": "MONOCRYSTALLINE" }
                  ],
                  "kits": [
                    { "name": "TEST Kit roto", "items": [ { "sku": "T-NO-EXISTE", "quantity": 1 }, { "sku": "T-NO-AH", "quantity": 1 } ] }
                  ]
                }
                """, false);

        assertThat(result.get("saved").asBoolean()).isFalse();
        assertThat(errorsOf(result)).containsExactly(
                "T-NO-AH: Falta «capacityAh».",
                "T-CHEM: «chemistry»: «NUCLEAR» no es válido; usa uno de: LIFEPO4, LITHIUM_ION, LEAD_ACID, GEL, AGM.",
                "T-TYPE: El tipo «PANEL» no existe; usa uno de: SOLAR_PANEL, BATTERY, INVERTER, CHARGE_CONTROLLER, CABLE, STRUCTURE.",
                "T-TYPO: Campos desconocidos para panel solar: «potencia».",
                "T-OK: El SKU «T-OK» está repetido en el archivo (también en el producto 1).",
                "TEST Kit roto: Producto 1 del kit: no existe un producto con SKU «T-NO-EXISTE» (ni en el archivo ni en la tienda).",
                "TEST Kit roto: Producto 2 del kit: el producto «T-NO-AH» tiene errores en este archivo.");
        assertThat(result.at("/rows/0/action").asText()).as("the valid row is reviewed...").isEqualTo("CREATE");
        assertThat(productRepository.findBySku("T-OK")).as("...but not saved").isEmpty();
    }

    @Test
    void aSkuCannotChangeItsProductType() throws Exception {
        importFile(CATALOG, false);

        JsonNode result = importFile("""
                { "products": [ { "type": "CABLE", "sku": "T-BAT-100", "name": "Cable", "price": 1, "lengthMeters": 2,
                                  "gauge": "AWG_10" } ] }
                """, true);

        assertThat(errorsOf(result)).containsExactly("T-BAT-100: El SKU «T-BAT-100» ya existe como batería; cambia el SKU o el tipo.");
    }

    @Test
    void aBrokenFileIsRejectedWithItsPosition() throws Exception {
        mockMvc.perform(post("/api/admin/catalog/import").header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"products\": [ { \"sku\": \"A\" } "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("El archivo no es un JSON válido (línea 1")));

        JsonNode result = importFile("{ \"productos\": [] }", true);
        assertThat(errorsOf(result)).containsExactly(
                "El archivo no tiene \"products\" ni \"kits\".",
                "Campos desconocidos en el archivo: «productos».");
    }
}
