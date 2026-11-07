package com.endel.demobox.service.catalog;

import com.endel.demobox.model.catalog.PhotoUrls;
import com.endel.demobox.exception.BadRequestException;
import com.endel.demobox.exception.ResourceNotFoundException;
import com.endel.demobox.model.catalog.ChargeController;
import com.endel.demobox.model.dto.catalog.ChargeControllerDto;
import com.endel.demobox.repository.catalog.ChargeControllerRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

@Service
public class ChargeControllerService {
    static final Set<Integer> BATTERY_VOLTAGES = Set.of(12, 24, 48);

    private final ChargeControllerRepository repository;

    public ChargeControllerService(ChargeControllerRepository repository) {
        this.repository = repository;
    }

    public List<ChargeControllerDto> findAll() {
        return repository.findAll().stream().map(this::toDto).toList();
    }

    public ChargeControllerDto getById(Long id) {
        return toDto(findEntity(id));
    }

    public ChargeControllerDto create(ChargeControllerDto dto) {
        ChargeController controller = new ChargeController();
        applyDto(controller, dto);
        return toDto(repository.save(controller));
    }

    public ChargeControllerDto update(Long id, ChargeControllerDto dto) {
        ChargeController controller = findEntity(id);
        applyDto(controller, dto);
        return toDto(repository.save(controller));
    }

    public void delete(Long id) {
        repository.delete(findEntity(id));
    }

    private ChargeController findEntity(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Charge controller not found: " + id));
    }

    private void applyDto(ChargeController controller, ChargeControllerDto dto) {
        if (!BATTERY_VOLTAGES.containsAll(dto.getSupportedBatteryVoltages())) {
            throw new BadRequestException("Los voltajes de batería válidos son 12, 24 y 48 V");
        }
        controller.setSku(dto.getSku());
        controller.setName(dto.getName());
        controller.setBrand(dto.getBrand());
        controller.setDescription(dto.getDescription());
        controller.setPrice(dto.getPrice());
        controller.setStockQuantity(dto.getStockQuantity());
        controller.setPhotos(PhotoUrls.resolve(dto.getImageUrls(), dto.getImageUrl()));
        controller.setReferenceUrl(dto.getReferenceUrl());
        controller.setActive(dto.isActive());
        // Mutate the managed collection rather than replacing it, so Hibernate tracks the change.
        controller.getSupportedBatteryVoltages().clear();
        controller.getSupportedBatteryVoltages().addAll(dto.getSupportedBatteryVoltages());
        controller.setMaxChargeCurrentA(dto.getMaxChargeCurrentA());
        controller.setMaxInputVoltage(dto.getMaxInputVoltage());
        controller.setMpptMinVoltage(dto.getMpptMinVoltage());
        controller.setMpptMaxInputCurrentA(dto.getMpptMaxInputCurrentA());
        controller.setMaxPvInputPowerW(dto.getMaxPvInputPowerW());
        controller.setMpptChannels(dto.getMpptChannels());
    }

    private ChargeControllerDto toDto(ChargeController controller) {
        ChargeControllerDto dto = new ChargeControllerDto();
        dto.setId(controller.getId());
        dto.setSku(controller.getSku());
        dto.setCode(controller.getCode());
        dto.setName(controller.getName());
        dto.setBrand(controller.getBrand());
        dto.setDescription(controller.getDescription());
        dto.setPrice(controller.getPrice());
        dto.setStockQuantity(controller.getStockQuantity());
        dto.setImageUrls(controller.getPhotos());
        dto.setImageUrl(controller.getPhotos().isEmpty() ? null : controller.getPhotos().get(0));
        dto.setReferenceUrl(controller.getReferenceUrl());
        dto.setActive(controller.isActive());
        dto.setSupportedBatteryVoltages(new TreeSet<>(controller.getSupportedBatteryVoltages()));
        dto.setMaxChargeCurrentA(controller.getMaxChargeCurrentA());
        dto.setMaxInputVoltage(controller.getMaxInputVoltage());
        dto.setMpptMinVoltage(controller.getMpptMinVoltage());
        dto.setMpptMaxInputCurrentA(controller.getMpptMaxInputCurrentA());
        dto.setMaxPvInputPowerW(controller.getMaxPvInputPowerW());
        dto.setMpptChannels(controller.getMpptChannels());
        return dto;
    }
}
