package com.endel.demobox.service;

import com.endel.demobox.exception.BadRequestException;
import com.endel.demobox.model.StoreSettings;
import com.endel.demobox.model.dto.StoreSettingsDto;
import com.endel.demobox.repository.StoreSettingsRepository;
import org.springframework.stereotype.Service;

@Service
public class StoreSettingsService {
    static final String DEFAULT_PAYMENT_INSTRUCTIONS =
            "Pago en efectivo. Te contactaremos por WhatsApp para coordinar el pago y la entrega.";

    private final StoreSettingsRepository repository;

    public StoreSettingsService(StoreSettingsRepository repository) {
        this.repository = repository;
    }

    public StoreSettingsDto getCurrent() {
        return toDto(findOrCreate());
    }

    public StoreSettingsDto update(StoreSettingsDto dto) {
        StoreSettings settings = findOrCreate();
        String whatsapp = dto.getWhatsappNumber() == null || dto.getWhatsappNumber().isBlank()
                ? null
                : PhoneNumbers.normalizeMexican(dto.getWhatsappNumber())
                        .orElseThrow(() -> new BadRequestException("El número de WhatsApp debe tener 10 dígitos."));
        settings.setWhatsappNumber(whatsapp);
        settings.setPaymentInstructions(dto.getPaymentInstructions().trim());
        return toDto(repository.save(settings));
    }

    private StoreSettings findOrCreate() {
        return repository.findAll().stream()
                .findFirst()
                .orElseGet(() -> {
                    StoreSettings settings = new StoreSettings();
                    settings.setPaymentInstructions(DEFAULT_PAYMENT_INSTRUCTIONS);
                    return repository.save(settings);
                });
    }

    private StoreSettingsDto toDto(StoreSettings settings) {
        StoreSettingsDto dto = new StoreSettingsDto();
        dto.setWhatsappNumber(settings.getWhatsappNumber());
        dto.setPaymentInstructions(settings.getPaymentInstructions());
        dto.setUpdatedAt(settings.getUpdatedAt());
        return dto;
    }
}
