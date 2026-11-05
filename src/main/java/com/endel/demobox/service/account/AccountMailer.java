package com.endel.demobox.service.account;

import com.endel.demobox.service.EmailService;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Sends verification and password-reset links; never throws (failures are logged). */
@Component
@Log4j2
public class AccountMailer {
    private final EmailService emailService;
    private final SpringTemplateEngine templateEngine;
    private final String publicBaseUrl;

    public AccountMailer(EmailService emailService, SpringTemplateEngine templateEngine,
                         @Value("${app.public-base-url}") String publicBaseUrl) {
        this.emailService = emailService;
        this.templateEngine = templateEngine;
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAccountEmail(AccountEmailEvent event) {
        boolean verify = event.type() == AccountEmailEvent.Type.VERIFY_EMAIL;
        String path = verify ? "/cuenta/verificar?token=" : "/cuenta/restablecer?token=";
        Context context = new Context();
        context.setVariable("fullName", event.fullName());
        context.setVariable("link", publicBaseUrl + path + URLEncoder.encode(event.rawToken(), StandardCharsets.UTF_8));
        String subject = verify ? "Confirma tu correo · Baja Solar Supply" : "Restablece tu contraseña · Baja Solar Supply";
        try {
            emailService.sendHtml(event.email(), subject,
                    templateEngine.process(verify ? "email/verify-email" : "email/reset-password", context));
            log.info("Sent '{}' to {}", subject, event.email());
        } catch (Exception e) {
            log.warn("Could not send '{}' to {}", subject, event.email(), e);
        }
    }
}
