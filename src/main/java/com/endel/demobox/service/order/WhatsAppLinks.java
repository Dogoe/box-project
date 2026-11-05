package com.endel.demobox.service.order;

import com.endel.demobox.model.dto.order.OrderDto;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.stream.Collectors;

/** wa.me links open WhatsApp with a prefilled message; the sender still has to press send. */
public final class WhatsAppLinks {
    private WhatsAppLinks() {
    }

    /** Customer -> store message summarizing the order; null when no store number is configured. */
    public static String customerToStore(OrderDto order, String storeNumber) {
        if (storeNumber == null || storeNumber.isBlank()) {
            return null;
        }
        String items = order.getItems().stream()
                .map(item -> "- " + item.getQuantity() + " × " + item.getKitName())
                .collect(Collectors.joining("\n"));
        String message = "Hola, acabo de hacer el pedido " + order.getFolio() + " por " + money(order.getTotalMxn()) + " MXN:\n"
                + items + "\n"
                + "Quiero coordinar el pago en efectivo y la entrega en: " + order.getFullAddress();
        return "https://wa.me/52" + storeNumber + "?text=" + URLEncoder.encode(message, StandardCharsets.UTF_8).replace("+", "%20");
    }

    static String money(BigDecimal amount) {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.US);
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return "$" + format.format(amount);
    }
}
