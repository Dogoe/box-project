package com.endel.demobox.controller.account;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Runs the real security chain: what's public, what needs a customer token, and that accounts only see their own orders. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AccountControllerTest {

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
        panel.setSku("ACCOUNT-API-PANEL");
        panel.setName("Panel");
        panel.setPrice(BigDecimal.valueOf(100));
        panel.setWattage(400);
        panel.setTechnology(PanelTechnology.MONOCRYSTALLINE);
        productRepository.save(panel);
        Kit kit = new Kit();
        kit.setName("Account API Kit");
        kit.setActive(true);
        kit.setPriceOverride(new BigDecimal("1000.00"));
        KitItem item = new KitItem();
        item.setKit(kit);
        item.setProduct(panel);
        item.setQuantity(1);
        kit.getItems().add(item);
        kitId = kitRepository.save(kit).getId();
    }

    private String register(String email) throws Exception {
        String body = mockMvc.perform(post("/api/account/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Juan\",\"phone\":\"6461234567\",\"email\":\"" + email + "\",\"password\":\"secreta123\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return (String) objectMapper.readValue(body, Map.class).get("token");
    }

    private String placeOrder(String bearer) throws Exception {
        var request = post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                .content("{\"customerName\":\"Juan\",\"customerPhone\":\"6461234567\",\"street\":\"Calle 1\","
                        + "\"neighborhood\":\"Centro\",\"postalCode\":\"22800\",\"items\":[{\"kitId\":" + kitId + ",\"quantity\":1}]}");
        if (bearer != null) {
            request = request.header("Authorization", "Bearer " + bearer);
        }
        String body = mockMvc.perform(request).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return (String) objectMapper.readValue(body, Map.class).get("folio");
    }

    @Test
    void emailStatusTellsCheckoutWhetherToOfferLoginOrSignUp() throws Exception {
        register("juan@ejemplo.com");

        mockMvc.perform(post("/api/account/email-status").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"JUAN@ejemplo.com\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.registered").value(true));
        mockMvc.perform(post("/api/account/email-status").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"nueva@ejemplo.com\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.registered").value(false));
    }

    @Test
    void duplicateSignUpAndBadLoginReturnClearMessages() throws Exception {
        register("juan@ejemplo.com");

        mockMvc.perform(post("/api/account/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Juan\",\"phone\":\"6461234567\",\"email\":\"juan@ejemplo.com\",\"password\":\"secreta123\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Este correo ya tiene cuenta. Inicia sesión."));
        mockMvc.perform(post("/api/account/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"juan@ejemplo.com\",\"password\":\"incorrecta\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Correo o contraseña incorrectos."));
        mockMvc.perform(post("/api/account/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Ana\",\"phone\":\"6461234567\",\"email\":\"ana@ejemplo.com\",\"password\":\"corta\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La contraseña debe tener al menos 8 caracteres"));
    }

    @Test
    void accountEndpointsNeedACustomerToken() throws Exception {
        mockMvc.perform(get("/api/account/me")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/account/orders")).andExpect(status().isForbidden());

        String token = register("juan@ejemplo.com");
        mockMvc.perform(get("/api/account/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("juan@ejemplo.com"))
                .andExpect(jsonPath("$.emailVerified").value(false));
    }

    @Test
    void ordersPlacedWithATokenAreLinkedAndOnlyVisibleToTheirOwner() throws Exception {
        String juan = register("juan@ejemplo.com");
        String ana = register("ana@ejemplo.com");
        String folio = placeOrder(juan);
        placeOrder(null);

        mockMvc.perform(get("/api/account/orders").header("Authorization", "Bearer " + juan))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].folio").value(folio))
                .andExpect(jsonPath("$[0].customerEmail").value("juan@ejemplo.com"));
        mockMvc.perform(get("/api/account/orders").header("Authorization", "Bearer " + ana))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void anExpiredOrBogusTokenStillAllowsAGuestCheckout() throws Exception {
        placeOrder("not.a.valid.jwt");
    }

    @Test
    void accountPagesArePublic() throws Exception {
        mockMvc.perform(get("/cuenta")).andExpect(status().isOk());
        mockMvc.perform(get("/cuenta/olvide")).andExpect(status().isOk());
        mockMvc.perform(get("/cuenta/restablecer").param("token", "x")).andExpect(status().isOk());
        mockMvc.perform(get("/cuenta/verificar").param("token", "x")).andExpect(status().isOk());
    }
}
