package com.endel.demobox.service;

import com.endel.demobox.model.WiringSettings;
import com.endel.demobox.model.dto.WiringSettingsDto;
import com.endel.demobox.repository.WiringSettingsRepository;
import org.springframework.stereotype.Service;

@Service
public class WiringSettingsService {
    private static final double DEFAULT_BATTERY_TO_INVERTER_METERS = 2.0;
    private static final double DEFAULT_PV_TO_INVERTER_METERS = 10.0;

    private final WiringSettingsRepository repository;

    public WiringSettingsService(WiringSettingsRepository repository) {
        this.repository = repository;
    }

    public WiringSettingsDto getCurrent() {
        return toDto(findOrCreate());
    }

    public WiringSettingsDto update(WiringSettingsDto dto) {
        WiringSettings settings = findOrCreate();
        settings.setBatteryToInverterMeters(dto.getBatteryToInverterMeters());
        settings.setPvToInverterMeters(dto.getPvToInverterMeters());
        settings.setControllerToBatteryMeters(dto.getControllerToBatteryMeters());
        return toDto(repository.save(settings));
    }

    private WiringSettings findOrCreate() {
        return repository.findAll().stream()
                .findFirst()
                .orElseGet(() -> {
                    WiringSettings settings = new WiringSettings();
                    settings.setBatteryToInverterMeters(DEFAULT_BATTERY_TO_INVERTER_METERS);
                    settings.setPvToInverterMeters(DEFAULT_PV_TO_INVERTER_METERS);
                    return repository.save(settings);
                });
    }

    private WiringSettingsDto toDto(WiringSettings settings) {
        WiringSettingsDto dto = new WiringSettingsDto();
        dto.setBatteryToInverterMeters(settings.getBatteryToInverterMeters());
        dto.setPvToInverterMeters(settings.getPvToInverterMeters());
        dto.setControllerToBatteryMeters(settings.getControllerToBatteryMeters() != null
                ? settings.getControllerToBatteryMeters()
                : WiringSettingsDto.DEFAULT_CONTROLLER_TO_BATTERY_METERS);
        dto.setUpdatedAt(settings.getUpdatedAt());
        return dto;
    }
}
