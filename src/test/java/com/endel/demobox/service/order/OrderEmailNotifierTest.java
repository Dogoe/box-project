package com.endel.demobox.service.order;

import com.endel.demobox.model.catalog.Kit;
import com.endel.demobox.model.catalog.KitItem;
import com.endel.demobox.model.catalog.PanelTechnology;
import com.endel.demobox.model.catalog.SolarPanel;
import com.endel.demobox.model.dto.StoreSettingsDto;
import com.endel.demobox.model.dto.order.CreateOrderRequest;
import com.endel.demobox.model.dto.order.OrderItemRequest;
import com.endel.demobox.repository.catalog.KitRepository;
import com.endel.demobox.repository.catalog.ProductRepository;
import com.endel.demobox.repository.order.CustomerOrderRepository;
import com.endel.demobox.service.EmailService;
import com.endel.demobox.service.StoreSettingsService;
import jakarta.mail.MessagingException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * Not @Transactional on purpose: emails go out after the order's transaction commits, so the test
 * has to let it commit for real. Data is removed in tearDown.
 */
@SpringBootTest
@ActiveProfiles("test")
class OrderEmailNotifierTest {

    private static final String STORE_EMAIL = "tienda@example.com";

    @MockitoBean
    private EmailService emailService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private StoreSettingsService storeSettingsService;

    @Autowired
    private KitRepository kitRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CustomerOrderRepository orderRepository;

    private Kit kit;
    private SolarPanel panel;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        panel = new SolarPanel();
        panel.setSku("MAIL-PANEL-" + suffix);
        panel.setName("Panel");
        panel.setPrice(BigDecimal.valueOf(100));
        panel.setWattage(400);
        panel.setTechnology(PanelTechnology.MONOCRYSTALLINE);
        productRepository.save(panel);

        kit = new Kit();
        kit.setName("Mail Kit " + suffix);
        kit.setActive(true);
        kit.setPriceOverride(new BigDecimal("9750.00"));
        KitItem item = new KitItem();
        item.setKit(kit);
        item.setProduct(panel);
        item.setQuantity(1);
        kit.getItems().add(item);
        kit = kitRepository.save(kit);

        StoreSettingsDto settings = storeSettingsService.getCurrent();
        settings.setNotificationEmail(STORE_EMAIL);
        storeSettingsService.update(settings);
    }

    @AfterEach
    void tearDown() {
        orderRepository.deleteAll();
        kitRepository.delete(kit);
        productRepository.delete(panel);
        StoreSettingsDto settings = storeSettingsService.getCurrent();
        settings.setNotificationEmail(null);
        storeSettingsService.update(settings);
    }

    private CreateOrderRequest request(String customerEmail) {
        OrderItemRequest item = new OrderItemRequest();
        item.setKitId(kit.getId());
        item.setQuantity(1);
        CreateOrderRequest request = new CreateOrderRequest();
        request.setCustomerName("Juan Pérez");
        request.setCustomerPhone("6461234567");
        request.setCustomerEmail(customerEmail);
        request.setStreet("Calle 1");
        request.setNeighborhood("Centro");
        request.setPostalCode("22800");
        request.setItems(List.of(item));
        return request;
    }

    @Test
    void newOrderEmailsTheStoreAndTheCustomer() throws MessagingException {
        String folio = orderService.create(request("juan@example.com")).getFolio();

        verify(emailService, timeout(3000)).sendHtml(eq(STORE_EMAIL), eq("Nuevo pedido " + folio + " · $9,750.00 MXN"), contains("Juan Pérez"));
        verify(emailService, timeout(3000)).sendHtml(eq("juan@example.com"), eq("Recibimos tu pedido " + folio), contains("/pedido/" + folio));
    }

    @Test
    void statusChangesEmailOnlyTheCustomer() throws MessagingException {
        Long id = orderService.create(request("juan@example.com")).getId();
        String folio = orderService.getById(id).getFolio();
        verify(emailService, timeout(3000)).sendHtml(eq("juan@example.com"), startsWith("Recibimos tu pedido"), anyString());

        orderService.confirmPayment(id, new BigDecimal("9750"), null, "admin");
        verify(emailService, timeout(3000)).sendHtml(eq("juan@example.com"), eq("Recibimos tu pago · pedido " + folio), contains("recibimos tu pago"));

        orderService.markDelivered(id, "admin");
        verify(emailService, timeout(3000)).sendHtml(eq("juan@example.com"), eq("Tu pedido " + folio + " fue entregado"), anyString());

        // The store only hears about new orders.
        verify(emailService, after(500).times(1)).sendHtml(eq(STORE_EMAIL), anyString(), anyString());
    }

    @Test
    void customerWithoutEmailGetsNothingButTheStoreIsStillNotified() throws MessagingException {
        String folio = orderService.create(request(null)).getFolio();

        verify(emailService, timeout(3000)).sendHtml(eq(STORE_EMAIL), contains(folio), anyString());
        verify(emailService, after(500).times(1)).sendHtml(anyString(), contains(folio), anyString());
    }

    @Test
    void aFailingMailServerNeverBreaksTheOrder() throws MessagingException {
        doThrow(new MessagingException("SMTP down")).when(emailService).sendHtml(anyString(), anyString(), anyString());

        String folio = orderService.create(request("juan@example.com")).getFolio();

        verify(emailService, timeout(3000)).sendHtml(eq("juan@example.com"), contains(folio), anyString());
        orderService.findByFolio(folio).orElseThrow();
    }
}
