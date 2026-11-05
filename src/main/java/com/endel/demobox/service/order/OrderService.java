package com.endel.demobox.service.order;

import com.endel.demobox.exception.BadRequestException;
import com.endel.demobox.exception.InvalidOrderTransitionException;
import com.endel.demobox.exception.ResourceNotFoundException;
import com.endel.demobox.model.dto.catalog.KitDto;
import com.endel.demobox.model.dto.order.CreateOrderRequest;
import com.endel.demobox.model.dto.order.OrderDto;
import com.endel.demobox.model.dto.order.OrderItemDto;
import com.endel.demobox.model.dto.order.OrderItemRequest;
import com.endel.demobox.model.order.CustomerOrder;
import com.endel.demobox.model.order.OrderItem;
import com.endel.demobox.model.order.OrderStatus;
import com.endel.demobox.repository.order.CustomerOrderRepository;
import com.endel.demobox.service.ExchangeRateService;
import com.endel.demobox.service.PhoneNumbers;
import com.endel.demobox.service.catalog.KitService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class OrderService {
    /** Only delivery area for now. */
    public static final String SERVICE_CITY = "Ensenada, B.C.";
    static final int MAX_UNITS_PER_KIT = 10;

    // No 0/O, 1/I/L: folios get read aloud and typed from WhatsApp.
    private static final String FOLIO_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final int FOLIO_LENGTH = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final CustomerOrderRepository orderRepository;
    private final KitService kitService;
    private final ExchangeRateService exchangeRateService;

    public OrderService(CustomerOrderRepository orderRepository, KitService kitService, ExchangeRateService exchangeRateService) {
        this.orderRepository = orderRepository;
        this.kitService = kitService;
        this.exchangeRateService = exchangeRateService;
    }

    @Transactional
    public CustomerOrder create(CreateOrderRequest request) {
        String phone = PhoneNumbers.normalizeMexican(request.getCustomerPhone())
                .orElseThrow(() -> new BadRequestException("El teléfono debe tener 10 dígitos."));

        Map<Long, Integer> quantityByKitId = new LinkedHashMap<>();
        for (OrderItemRequest item : request.getItems()) {
            quantityByKitId.merge(item.getKitId(), item.getQuantity(), Integer::sum);
        }

        // Prices always come from the catalog, never from the client's cart.
        Map<Long, KitDto> activeKits = kitService.findAllActive().stream()
                .collect(Collectors.toMap(KitDto::getId, Function.identity()));

        CustomerOrder order = new CustomerOrder();
        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<Long, Integer> entry : quantityByKitId.entrySet()) {
            KitDto kit = activeKits.get(entry.getKey());
            if (kit == null) {
                throw new BadRequestException("Uno de los kits del carrito ya no está disponible; actualiza tu carrito.");
            }
            int quantity = entry.getValue();
            if (quantity > MAX_UNITS_PER_KIT) {
                throw new BadRequestException("Máximo " + MAX_UNITS_PER_KIT + " unidades por kit; para más, contáctanos.");
            }
            BigDecimal lineTotal = kit.getSellPrice().multiply(BigDecimal.valueOf(quantity));
            OrderItem item = new OrderItem();
            item.setOrder(order);
            item.setKitId(kit.getId());
            item.setKitName(kit.getName());
            item.setUnitPriceMxn(kit.getSellPrice());
            item.setQuantity(quantity);
            item.setLineTotalMxn(lineTotal);
            order.getItems().add(item);
            total = total.add(lineTotal);
        }

        order.setFolio(newFolio());
        order.setCustomerName(request.getCustomerName().trim());
        order.setCustomerPhone(phone);
        order.setCustomerEmail(blankToNull(request.getCustomerEmail()));
        order.setStreet(request.getStreet().trim());
        order.setNeighborhood(request.getNeighborhood().trim());
        order.setPostalCode(request.getPostalCode());
        order.setAddressReferences(blankToNull(request.getAddressReferences()));
        order.setCity(SERVICE_CITY);
        order.setNotes(blankToNull(request.getNotes()));
        order.setTotalMxn(total);
        order.setUsdToMxnRate(exchangeRateService.getCurrentRate().getUsdToMxnRate());
        return orderRepository.save(order);
    }

    public Optional<OrderDto> findByFolio(String folio) {
        return orderRepository.findByFolio(folio == null ? "" : folio.trim().toUpperCase()).map(this::toDto);
    }

    public List<OrderDto> findAll(OrderStatus status) {
        List<CustomerOrder> orders = status == null
                ? orderRepository.findAllByOrderByCreatedAtDesc()
                : orderRepository.findByStatusOrderByCreatedAtDesc(status);
        return orders.stream().map(this::toDto).toList();
    }

    public OrderDto getById(Long id) {
        return toDto(findEntity(id));
    }

    @Transactional
    public OrderDto confirmPayment(Long id, BigDecimal amountMxn, String note, String admin) {
        CustomerOrder order = findEntity(id);
        requireStatus(order, "confirmar el pago", OrderStatus.PENDING);
        markPaid(order, amountMxn, note, admin);
        order.setStatus(OrderStatus.PAID);
        return toDto(orderRepository.save(order));
    }

    @Transactional
    public OrderDto markDelivered(Long id, String admin) {
        CustomerOrder order = findEntity(id);
        requireStatus(order, "marcar como entregado", OrderStatus.PAID);
        markDelivered(order, admin);
        return toDto(orderRepository.save(order));
    }

    /** Cash on delivery: payment and delivery confirmed in one step. */
    @Transactional
    public OrderDto confirmPaymentAndDelivery(Long id, BigDecimal amountMxn, String note, String admin) {
        CustomerOrder order = findEntity(id);
        requireStatus(order, "cobrar y entregar", OrderStatus.PENDING);
        markPaid(order, amountMxn, note, admin);
        markDelivered(order, admin);
        return toDto(orderRepository.save(order));
    }

    @Transactional
    public OrderDto cancel(Long id, String reason, String admin) {
        CustomerOrder order = findEntity(id);
        requireStatus(order, "cancelar", OrderStatus.PENDING, OrderStatus.PAID);
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelledAt(LocalDateTime.now());
        order.setCancelledBy(admin);
        order.setCancelReason(reason.trim());
        return toDto(orderRepository.save(order));
    }

    private void markPaid(CustomerOrder order, BigDecimal amountMxn, String note, String admin) {
        order.setPaidAt(LocalDateTime.now());
        order.setPaidAmountMxn(amountMxn);
        order.setPaymentNote(blankToNull(note));
        order.setPaidBy(admin);
    }

    private void markDelivered(CustomerOrder order, String admin) {
        order.setStatus(OrderStatus.DELIVERED);
        order.setDeliveredAt(LocalDateTime.now());
        order.setDeliveredBy(admin);
    }

    private static void requireStatus(CustomerOrder order, String action, OrderStatus... allowed) {
        for (OrderStatus status : allowed) {
            if (order.getStatus() == status) {
                return;
            }
        }
        throw new InvalidOrderTransitionException("No se puede " + action + " un pedido " + order.getStatus().getLabel().toLowerCase() + ".");
    }

    private CustomerOrder findEntity(Long id) {
        return orderRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Order not found: " + id));
    }

    private String newFolio() {
        String folio;
        do {
            StringBuilder code = new StringBuilder("BSS-");
            for (int i = 0; i < FOLIO_LENGTH; i++) {
                code.append(FOLIO_ALPHABET.charAt(RANDOM.nextInt(FOLIO_ALPHABET.length())));
            }
            folio = code.toString();
        } while (orderRepository.existsByFolio(folio));
        return folio;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private OrderDto toDto(CustomerOrder order) {
        OrderDto dto = new OrderDto();
        dto.setId(order.getId());
        dto.setFolio(order.getFolio());
        dto.setStatus(order.getStatus());
        dto.setStatusLabel(order.getStatus().getLabel());
        dto.setCustomerName(order.getCustomerName());
        dto.setCustomerPhone(order.getCustomerPhone());
        dto.setCustomerEmail(order.getCustomerEmail());
        dto.setStreet(order.getStreet());
        dto.setNeighborhood(order.getNeighborhood());
        dto.setPostalCode(order.getPostalCode());
        dto.setAddressReferences(order.getAddressReferences());
        dto.setCity(order.getCity());
        dto.setNotes(order.getNotes());
        dto.setTotalMxn(order.getTotalMxn());
        dto.setItems(order.getItems().stream().map(item -> {
            OrderItemDto itemDto = new OrderItemDto();
            itemDto.setKitId(item.getKitId());
            itemDto.setKitName(item.getKitName());
            itemDto.setUnitPriceMxn(item.getUnitPriceMxn());
            itemDto.setQuantity(item.getQuantity());
            itemDto.setLineTotalMxn(item.getLineTotalMxn());
            return itemDto;
        }).toList());
        dto.setCreatedAt(order.getCreatedAt());
        dto.setPaidAt(order.getPaidAt());
        dto.setPaidAmountMxn(order.getPaidAmountMxn());
        dto.setPaymentNote(order.getPaymentNote());
        dto.setPaidBy(order.getPaidBy());
        dto.setDeliveredAt(order.getDeliveredAt());
        dto.setDeliveredBy(order.getDeliveredBy());
        dto.setCancelledAt(order.getCancelledAt());
        dto.setCancelledBy(order.getCancelledBy());
        dto.setCancelReason(order.getCancelReason());
        return dto;
    }
}
