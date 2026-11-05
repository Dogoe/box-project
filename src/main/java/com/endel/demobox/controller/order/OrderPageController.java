package com.endel.demobox.controller.order;

import com.endel.demobox.model.dto.StoreSettingsDto;
import com.endel.demobox.model.dto.order.OrderDto;
import com.endel.demobox.service.StoreSettingsService;
import com.endel.demobox.service.catalog.KitService;
import com.endel.demobox.service.order.OrderService;
import com.endel.demobox.service.order.WhatsAppLinks;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
public class OrderPageController {
    private final KitService kitService;
    private final OrderService orderService;
    private final StoreSettingsService storeSettingsService;

    public OrderPageController(KitService kitService, OrderService orderService, StoreSettingsService storeSettingsService) {
        this.kitService = kitService;
        this.orderService = orderService;
        this.storeSettingsService = storeSettingsService;
    }

    @GetMapping("/carrito")
    public String cart(Model model) {
        // The cart lives in the browser as kit ids; the page needs names/prices to show it.
        List<Map<String, Object>> kits = kitService.findAllActive().stream()
                .map(kit -> Map.<String, Object>of(
                        "id", kit.getId(),
                        "name", kit.getName(),
                        "sellPrice", kit.getSellPrice(),
                        "imageUrl", kit.getImageUrl() == null ? "" : kit.getImageUrl()))
                .toList();
        model.addAttribute("cartKits", kits);
        model.addAttribute("serviceCity", OrderService.SERVICE_CITY);
        return "carrito";
    }

    @GetMapping("/pedido/{folio}")
    public String order(@PathVariable String folio, Model model, HttpServletResponse response) {
        Optional<OrderDto> order = orderService.findByFolio(folio);
        if (order.isEmpty()) {
            response.setStatus(HttpStatus.NOT_FOUND.value());
            model.addAttribute("folio", folio);
            return "pedido";
        }
        StoreSettingsDto settings = storeSettingsService.getCurrent();
        model.addAttribute("order", order.get());
        model.addAttribute("paymentInstructions", settings.getPaymentInstructions());
        model.addAttribute("whatsappUrl", WhatsAppLinks.customerToStore(order.get(), settings.getWhatsappNumber()));
        model.addAttribute("whatsappQuestionUrl", WhatsAppLinks.customerQuestion(order.get(), settings.getWhatsappNumber()));
        return "pedido";
    }
}
