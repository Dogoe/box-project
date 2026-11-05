package com.endel.demobox.service.account;

import com.endel.demobox.exception.BadRequestException;
import com.endel.demobox.exception.ConflictException;
import com.endel.demobox.exception.InvalidCredentialsException;
import com.endel.demobox.model.User;
import com.endel.demobox.model.catalog.Kit;
import com.endel.demobox.model.catalog.KitItem;
import com.endel.demobox.model.catalog.PanelTechnology;
import com.endel.demobox.model.catalog.SolarPanel;
import com.endel.demobox.model.dto.account.AuthResponse;
import com.endel.demobox.model.dto.account.RegisterAccountRequest;
import com.endel.demobox.model.dto.order.CreateOrderRequest;
import com.endel.demobox.model.dto.order.OrderItemRequest;
import com.endel.demobox.model.order.CustomerOrder;
import com.endel.demobox.repository.UserTokenRepository;
import com.endel.demobox.repository.catalog.KitRepository;
import com.endel.demobox.repository.catalog.ProductRepository;
import com.endel.demobox.service.order.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@RecordApplicationEvents
class AccountServiceTest {

    @Autowired
    private AccountService accountService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private UserTokenRepository tokenRepository;

    @Autowired
    private KitRepository kitRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ApplicationEvents events;

    private Kit kit;

    @BeforeEach
    void setUp() {
        SolarPanel panel = new SolarPanel();
        panel.setSku("ACCOUNT-TEST-PANEL");
        panel.setName("Panel");
        panel.setPrice(BigDecimal.valueOf(100));
        panel.setWattage(400);
        panel.setTechnology(PanelTechnology.MONOCRYSTALLINE);
        productRepository.save(panel);
        kit = new Kit();
        kit.setName("Account Test Kit");
        kit.setActive(true);
        kit.setPriceOverride(new BigDecimal("1000.00"));
        KitItem item = new KitItem();
        item.setKit(kit);
        item.setProduct(panel);
        item.setQuantity(1);
        kit.getItems().add(item);
        kit = kitRepository.save(kit);
    }

    private static RegisterAccountRequest registration(String email) {
        RegisterAccountRequest request = new RegisterAccountRequest();
        request.setFullName("Juan Pérez");
        request.setPhone("646 123 4567");
        request.setEmail(email);
        request.setPassword("secreta123");
        return request;
    }

    private CreateOrderRequest order(String email) {
        OrderItemRequest item = new OrderItemRequest();
        item.setKitId(kit.getId());
        item.setQuantity(1);
        CreateOrderRequest request = new CreateOrderRequest();
        request.setCustomerName("Juan Pérez");
        request.setCustomerPhone("6461234567");
        request.setCustomerEmail(email);
        request.setStreet("Av. Reforma 123");
        request.setNeighborhood("Centro");
        request.setPostalCode("22800");
        request.setAddressReferences("Portón negro");
        request.setItems(List.of(item));
        return request;
    }

    private String lastLinkToken(AccountEmailEvent.Type type) {
        return events.stream(AccountEmailEvent.class)
                .filter(e -> e.type() == type)
                .reduce((first, second) -> second)
                .orElseThrow()
                .rawToken();
    }

    @Test
    void register_normalizesTheEmailSendsAVerificationLinkAndLogsIn() {
        AuthResponse response = accountService.register(registration("  Juan@Ejemplo.COM "));

        assertNotNull(response.getToken());
        assertEquals("juan@ejemplo.com", response.getAccount().getEmail());
        assertEquals("6461234567", response.getAccount().getPhone());
        assertFalse(response.getAccount().isEmailVerified());
        assertTrue(accountService.isRegistered("JUAN@ejemplo.com"));
        assertEquals("juan@ejemplo.com", events.stream(AccountEmailEvent.class).findFirst().orElseThrow().email());
    }

    @Test
    void register_rejectsAnEmailThatAlreadyHasAnAccount() {
        accountService.register(registration("juan@ejemplo.com"));

        ConflictException ex = assertThrows(ConflictException.class, () -> accountService.register(registration("JUAN@ejemplo.com")));
        assertTrue(ex.getMessage().contains("Inicia sesión"));
    }

    @Test
    void login_checksThePassword() {
        accountService.register(registration("juan@ejemplo.com"));

        assertNotNull(accountService.login("Juan@Ejemplo.com", "secreta123").getToken());
        assertThrows(InvalidCredentialsException.class, () -> accountService.login("juan@ejemplo.com", "otra-cosa"));
        assertThrows(InvalidCredentialsException.class, () -> accountService.login("nadie@ejemplo.com", "secreta123"));
    }

    @Test
    void verifyingTheEmailClaimsEarlierGuestOrdersWithThatEmailOnly() {
        CustomerOrder mine = orderService.create(order("JUAN@ejemplo.com"));
        CustomerOrder someoneElses = orderService.create(order("otra@ejemplo.com"));
        accountService.register(registration("juan@ejemplo.com"));
        User user = accountService.findByEmail("juan@ejemplo.com").orElseThrow();

        // Registering alone doesn't attach anything: the email isn't proven yet.
        assertTrue(orderService.findByCustomer(user.getId()).isEmpty());

        String token = lastLinkToken(AccountEmailEvent.Type.VERIFY_EMAIL);
        assertEquals(Optional.of(1), accountService.verifyEmail(token));

        assertTrue(accountService.findByEmail("juan@ejemplo.com").orElseThrow().isEmailVerified());
        List<String> claimed = orderService.findByCustomer(user.getId()).stream().map(o -> o.getFolio()).toList();
        assertEquals(List.of(mine.getFolio()), claimed);
        assertFalse(claimed.contains(someoneElses.getFolio()));

        // Links are single-use.
        assertEquals(Optional.empty(), accountService.verifyEmail(token));
        assertEquals(Optional.empty(), accountService.verifyEmail("not-a-real-token"));
    }

    @Test
    void expiredLinksAreRejected() {
        accountService.register(registration("juan@ejemplo.com"));
        String token = lastLinkToken(AccountEmailEvent.Type.VERIFY_EMAIL);
        tokenRepository.findAll().forEach(t -> {
            t.setExpiresAt(LocalDateTime.now().minusMinutes(1));
            tokenRepository.save(t);
        });

        assertEquals(Optional.empty(), accountService.verifyEmail(token));
    }

    @Test
    void passwordReset_changesThePasswordVerifiesTheEmailAndIsSingleUse() {
        accountService.register(registration("juan@ejemplo.com"));

        accountService.forgotPassword("nadie@ejemplo.com");
        assertEquals(0, events.stream(AccountEmailEvent.class).filter(e -> e.type() == AccountEmailEvent.Type.RESET_PASSWORD).count());

        accountService.forgotPassword("Juan@ejemplo.com");
        String resetToken = lastLinkToken(AccountEmailEvent.Type.RESET_PASSWORD);

        // A verification token can't be used to reset a password.
        String verifyToken = lastLinkToken(AccountEmailEvent.Type.VERIFY_EMAIL);
        assertThrows(BadRequestException.class, () -> accountService.resetPassword(verifyToken, "nueva-clave-1"));

        AuthResponse response = accountService.resetPassword(resetToken, "nueva-clave-1");
        assertNotNull(response.getToken());
        assertTrue(response.getAccount().isEmailVerified());
        assertNotNull(accountService.login("juan@ejemplo.com", "nueva-clave-1").getToken());
        assertThrows(InvalidCredentialsException.class, () -> accountService.login("juan@ejemplo.com", "secreta123"));
        assertThrows(BadRequestException.class, () -> accountService.resetPassword(resetToken, "otra-clave-2"));
    }

    @Test
    void orderPlacedWhileLoggedInIsLinkedAndRemembersTheAddress() {
        accountService.register(registration("juan@ejemplo.com"));
        User user = accountService.findByEmail("juan@ejemplo.com").orElseThrow();

        CustomerOrder created = orderService.create(order("otro-correo@ejemplo.com"), user);

        assertEquals(user.getId(), created.getUserId());
        assertEquals("juan@ejemplo.com", created.getCustomerEmail());
        User reloaded = accountService.findByEmail("juan@ejemplo.com").orElseThrow();
        assertEquals("Av. Reforma 123", reloaded.getDefaultStreet());
        assertEquals("22800", reloaded.getDefaultPostalCode());
        assertEquals("Portón negro", reloaded.getDefaultAddressReferences());
        assertNull(orderService.create(order("juan@ejemplo.com")).getUserId(), "guest orders are never linked by email alone");
    }
}
