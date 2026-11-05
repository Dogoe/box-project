package com.endel.demobox.service.order;

import com.endel.demobox.exception.BadRequestException;
import com.endel.demobox.exception.InvalidOrderTransitionException;
import com.endel.demobox.model.catalog.Kit;
import com.endel.demobox.model.catalog.KitItem;
import com.endel.demobox.model.catalog.PanelTechnology;
import com.endel.demobox.model.catalog.SolarPanel;
import com.endel.demobox.model.dto.order.CreateOrderRequest;
import com.endel.demobox.model.dto.order.OrderDto;
import com.endel.demobox.model.dto.order.OrderItemRequest;
import com.endel.demobox.model.order.CustomerOrder;
import com.endel.demobox.model.order.OrderStatus;
import com.endel.demobox.repository.catalog.KitRepository;
import com.endel.demobox.repository.catalog.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OrderServiceTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private KitRepository kitRepository;

    @Autowired
    private ProductRepository productRepository;

    private Kit kitA;
    private Kit kitB;

    private Kit kit(String name, BigDecimal price, boolean active, SolarPanel panel) {
        Kit kit = new Kit();
        kit.setName(name);
        kit.setActive(active);
        kit.setPriceOverride(price);
        KitItem item = new KitItem();
        item.setKit(kit);
        item.setProduct(panel);
        item.setQuantity(1);
        kit.getItems().add(item);
        return kitRepository.save(kit);
    }

    @BeforeEach
    void setUp() {
        SolarPanel panel = new SolarPanel();
        panel.setSku("ORDER-TEST-PANEL");
        panel.setName("Panel");
        panel.setPrice(BigDecimal.valueOf(100));
        panel.setWattage(400);
        panel.setTechnology(PanelTechnology.MONOCRYSTALLINE);
        productRepository.save(panel);
        kitA = kit("Order Test Kit A", new BigDecimal("9750.00"), true, panel);
        kitB = kit("Order Test Kit B", new BigDecimal("1000.00"), true, panel);
    }

    private static OrderItemRequest item(Long kitId, int quantity) {
        OrderItemRequest item = new OrderItemRequest();
        item.setKitId(kitId);
        item.setQuantity(quantity);
        return item;
    }

    private static CreateOrderRequest request(OrderItemRequest... items) {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setCustomerName("Juan Pérez");
        request.setCustomerPhone("+52 (646) 123-4567");
        request.setStreet("Calle Ejemplo 123");
        request.setNeighborhood("Centro");
        request.setPostalCode("22800");
        request.setItems(new ArrayList<>(List.of(items)));
        return request;
    }

    @Test
    void create_pricesFromTheCatalogSnapshotsThemAndFixesTheCity() {
        CustomerOrder order = orderService.create(request(item(kitA.getId(), 1), item(kitB.getId(), 2)));

        assertTrue(order.getFolio().matches("BSS-[A-HJKMNP-Z2-9]{8}"), order.getFolio());
        assertEquals(OrderStatus.PENDING, order.getStatus());
        assertEquals(0, new BigDecimal("11750.00").compareTo(order.getTotalMxn())); // 9750 + 2 x 1000
        assertEquals("6461234567", order.getCustomerPhone());
        assertEquals("Ensenada, B.C.", order.getCity());
        assertEquals("Order Test Kit A", order.getItems().get(0).getKitName());

        // Later price changes don't touch the order.
        kitA.setPriceOverride(new BigDecimal("1.00"));
        kitRepository.save(kitA);
        OrderDto reloaded = orderService.findByFolio(order.getFolio()).orElseThrow();
        assertEquals(0, new BigDecimal("11750.00").compareTo(reloaded.getTotalMxn()));
    }

    @Test
    void create_mergesRepeatedKitsAndCapsUnitsPerKit() {
        CustomerOrder order = orderService.create(request(item(kitA.getId(), 2), item(kitA.getId(), 3)));
        assertEquals(1, order.getItems().size());
        assertEquals(5, order.getItems().get(0).getQuantity());

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> orderService.create(request(item(kitA.getId(), 6), item(kitA.getId(), 5))));
        assertTrue(ex.getMessage().contains("Máximo 10"));
    }

    @Test
    void create_rejectsInactiveOrUnknownKitsAndBadPhones() {
        kitA.setActive(false);
        kitRepository.save(kitA);
        assertThrows(BadRequestException.class, () -> orderService.create(request(item(kitA.getId(), 1))));
        assertThrows(BadRequestException.class, () -> orderService.create(request(item(999_999L, 1))));

        CreateOrderRequest badPhone = request(item(kitB.getId(), 1));
        badPhone.setCustomerPhone("12345");
        assertThrows(BadRequestException.class, () -> orderService.create(badPhone));
    }

    @Test
    void paymentThenDelivery_recordsWhoAndWhen() {
        Long id = orderService.create(request(item(kitB.getId(), 1))).getId();

        OrderDto paid = orderService.confirmPayment(id, new BigDecimal("1000"), "Efectivo en local", "admin@test");
        assertEquals(OrderStatus.PAID, paid.getStatus());
        assertEquals("admin@test", paid.getPaidBy());
        assertNotNull(paid.getPaidAt());

        OrderDto delivered = orderService.markDelivered(id, "admin@test");
        assertEquals(OrderStatus.DELIVERED, delivered.getStatus());
        assertNotNull(delivered.getDeliveredAt());
    }

    @Test
    void cashOnDelivery_paysAndDeliversInOneStep() {
        Long id = orderService.create(request(item(kitB.getId(), 1))).getId();

        OrderDto done = orderService.confirmPaymentAndDelivery(id, new BigDecimal("1000"), null, "admin@test");

        assertEquals(OrderStatus.DELIVERED, done.getStatus());
        assertNotNull(done.getPaidAt());
        assertNotNull(done.getDeliveredAt());
    }

    @Test
    void invalidTransitionsAreRejected() {
        Long id = orderService.create(request(item(kitB.getId(), 1))).getId();
        assertThrows(InvalidOrderTransitionException.class, () -> orderService.markDelivered(id, "admin"));

        orderService.confirmPaymentAndDelivery(id, new BigDecimal("1000"), null, "admin");
        InvalidOrderTransitionException ex = assertThrows(InvalidOrderTransitionException.class,
                () -> orderService.cancel(id, "ya no lo quiere", "admin"));
        assertTrue(ex.getMessage().contains("entregado"));
    }

    @Test
    void paidOrdersCanStillBeCancelled() {
        Long id = orderService.create(request(item(kitB.getId(), 1))).getId();
        orderService.confirmPayment(id, new BigDecimal("1000"), null, "admin");

        OrderDto cancelled = orderService.cancel(id, "Cliente se mudó", "admin");

        assertEquals(OrderStatus.CANCELLED, cancelled.getStatus());
        assertEquals("Cliente se mudó", cancelled.getCancelReason());
    }
}
