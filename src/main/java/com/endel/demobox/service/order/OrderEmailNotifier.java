package com.endel.demobox.service.order;

import com.endel.demobox.model.dto.StoreSettingsDto;
import com.endel.demobox.model.dto.order.OrderDto;
import com.endel.demobox.service.EmailService;
import com.endel.demobox.service.StoreSettingsService;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

/**
 * Emails the store about new orders and the customer (when they left an email) about every
 * status change. Runs after the order is committed and off the request thread, and never throws:
 * a mail failure is logged, it can't block or undo an order.
 */
@Component
@Log4j2
public class OrderEmailNotifier {
    private final OrderService orderService;
    private final StoreSettingsService storeSettingsService;
    private final EmailService emailService;
    private final SpringTemplateEngine templateEngine;
    private final String publicBaseUrl;

    public OrderEmailNotifier(OrderService orderService,
                              StoreSettingsService storeSettingsService,
                              EmailService emailService,
                              SpringTemplateEngine templateEngine,
                              @Value("${app.public-base-url}") String publicBaseUrl) {
        this.orderService = orderService;
        this.storeSettingsService = storeSettingsService;
        this.emailService = emailService;
        this.templateEngine = templateEngine;
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onOrderEvent(OrderEvent event) {
        OrderDto order;
        StoreSettingsDto settings;
        try {
            order = orderService.getById(event.orderId());
            settings = storeSettingsService.getCurrent();
        } catch (Exception e) {
            log.warn("Could not load order {} for its {} email", event.orderId(), event.type(), e);
            return;
        }

        Context context = new Context();
        context.setVariable("order", order);
        context.setVariable("event", event.type().name());
        context.setVariable("orderUrl", publicBaseUrl + "/pedido/" + order.getFolio());
        context.setVariable("adminUrl", publicBaseUrl + "/admin");
        context.setVariable("paymentInstructions", settings.getPaymentInstructions());
        String total = WhatsAppLinks.money(order.getTotalMxn()) + " MXN";

        if (event.type() == OrderEvent.Type.CREATED && settings.getNotificationEmail() != null) {
            send(settings.getNotificationEmail(), "Nuevo pedido " + order.getFolio() + " · " + total, "email/order-store", context);
        }
        if (order.getCustomerEmail() != null) {
            send(order.getCustomerEmail(), customerSubject(event.type(), order.getFolio()), "email/order-customer", context);
        }
    }

    private static String customerSubject(OrderEvent.Type type, String folio) {
        return switch (type) {
            case CREATED -> "Recibimos tu pedido " + folio;
            case PAID -> "Recibimos tu pago · pedido " + folio;
            case DELIVERED -> "Tu pedido " + folio + " fue entregado";
            case CANCELLED -> "Tu pedido " + folio + " fue cancelado";
        };
    }

    private void send(String to, String subject, String template, Context context) {
        try {
            emailService.sendHtml(to, subject, templateEngine.process(template, context));
            log.info("Sent '{}' to {}", subject, to);
        } catch (Exception e) {
            log.warn("Could not send '{}' to {}", subject, to, e);
        }
    }
}
