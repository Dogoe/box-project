package com.endel.demobox.controller.order;

import com.endel.demobox.model.catalog.Kit;
import com.endel.demobox.model.catalog.KitItem;
import com.endel.demobox.model.catalog.PanelTechnology;
import com.endel.demobox.model.catalog.SolarPanel;
import com.endel.demobox.repository.catalog.KitRepository;
import com.endel.demobox.repository.catalog.ProductRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Runs the real security chain: checkout and the order page are public, managing orders is admin-only. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private KitRepository kitRepository;

    @Autowired
    private ProductRepository productRepository;

    private Long kitId;

    @BeforeEach
    void setUp() {
        SolarPanel panel = new SolarPanel();
        panel.setSku("ORDER-API-PANEL");
        panel.setName("Panel");
        panel.setPrice(BigDecimal.valueOf(100));
        panel.setWattage(400);
        panel.setTechnology(PanelTechnology.MONOCRYSTALLINE);
        productRepository.save(panel);

        Kit kit = new Kit();
        kit.setName("Order API Kit");
        kit.setActive(true);
        kit.setPriceOverride(new BigDecimal("5000.00"));
        KitItem item = new KitItem();
        item.setKit(kit);
        item.setProduct(panel);
        item.setQuantity(1);
        kit.getItems().add(item);
        kitId = kitRepository.save(kit).getId();
    }

    private String adminToken() throws Exception {
        String body = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@bajasolarsupply.com\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        @SuppressWarnings("unchecked")
        Map<String, Object> response = objectMapper.readValue(body, Map.class);
        return (String) response.get("token");
    }

    private String orderJson(String phone) {
        return "{\"customerName\":\"Ana\",\"customerPhone\":\"" + phone + "\",\"street\":\"Calle 1\","
                + "\"neighborhood\":\"Centro\",\"postalCode\":\"22800\","
                + "\"items\":[{\"kitId\":" + kitId + ",\"quantity\":1}]}";
    }

    @Test
    void guestCanPlaceAnOrderAndOpenItsPageByFolio() throws Exception {
        String body = mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(orderJson("6461234567")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.folio").exists())
                .andReturn().getResponse().getContentAsString();
        String folio = (String) objectMapper.readValue(body, Map.class).get("folio");

        mockMvc.perform(get("/pedido/" + folio))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(folio)))
                .andExpect(content().string(containsString("Order API Kit")));
    }

    @Test
    void unknownFolioIsA404Page() throws Exception {
        mockMvc.perform(get("/pedido/BSS-NOPE2345")).andExpect(status().isNotFound());
    }

    @Test
    void invalidCheckoutReturnsTheFieldMessage() throws Exception {
        mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(orderJson("123")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El teléfono debe tener 10 dígitos."));
    }

    @Test
    void managingOrdersRequiresAdmin() throws Exception {
        mockMvc.perform(get("/api/admin/orders")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/settings/store")).andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/orders").header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk());
    }
}
