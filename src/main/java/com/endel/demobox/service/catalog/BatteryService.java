package com.endel.demobox.service.catalog;

import com.endel.demobox.model.catalog.PhotoUrls;
import com.endel.demobox.model.catalog.TerminalSize;
import com.endel.demobox.exception.ResourceNotFoundException;
import com.endel.demobox.model.catalog.Battery;
import com.endel.demobox.model.catalog.BatteryChemistry;
import com.endel.demobox.model.dto.catalog.BatteryDto;
import com.endel.demobox.repository.catalog.BatteryRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BatteryService {
    private final BatteryRepository repository;

    public BatteryService(BatteryRepository repository) {
        this.repository = repository;
    }

    public List<BatteryDto> findAll() {
        return repository.findAll().stream().map(this::toDto).toList();
    }

    public List<BatteryDto> findAllActive() {
        return repository.findAll().stream()
                .filter(Battery::isActive)
                .map(this::toDto)
                .toList();
    }

    public BatteryDto getById(Long id) {
        return toDto(findEntity(id));
    }

    public BatteryDto create(BatteryDto dto) {
        Battery battery = new Battery();
        applyDto(battery, dto);
        return toDto(repository.save(battery));
    }

    public BatteryDto update(Long id, BatteryDto dto) {
        Battery battery = findEntity(id);
        applyDto(battery, dto);
        return toDto(repository.save(battery));
    }

    public void delete(Long id) {
        repository.delete(findEntity(id));
    }

    private Battery findEntity(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Battery not found: " + id));
    }

    private void applyDto(Battery battery, BatteryDto dto) {
        battery.setSku(dto.getSku());
        battery.setName(dto.getName());
        battery.setBrand(dto.getBrand());
        battery.setDescription(dto.getDescription());
        battery.setPrice(dto.getPrice());
        battery.setStockQuantity(dto.getStockQuantity());
        battery.setPhotos(PhotoUrls.resolve(dto.getImageUrls(), dto.getImageUrl()));
        battery.setReferenceUrl(dto.getReferenceUrl());
        battery.setActive(dto.isActive());
        battery.setCapacityAh(dto.getCapacityAh());
        battery.setVoltage(dto.getVoltage());
        battery.setChemistry(dto.getChemistry() != null ? dto.getChemistry() : BatteryChemistry.LIFEPO4);
        battery.setCycleLife(dto.getCycleLife());
        battery.setBmsMaxDischargeA(dto.getBmsMaxDischargeA());
        battery.setIntegratedBreakerA(dto.getIntegratedBreakerA());
        battery.setTerminalSize(TerminalSize.orDefault(dto.getTerminalSize()));
    }

    private BatteryDto toDto(Battery battery) {
        BatteryDto dto = new BatteryDto();
        dto.setId(battery.getId());
        dto.setSku(battery.getSku());
        dto.setName(battery.getName());
        dto.setBrand(battery.getBrand());
        dto.setDescription(battery.getDescription());
        dto.setPrice(battery.getPrice());
        dto.setStockQuantity(battery.getStockQuantity());
        dto.setImageUrls(battery.getPhotos());
        dto.setImageUrl(battery.getPhotos().isEmpty() ? null : battery.getPhotos().get(0));
        dto.setReferenceUrl(battery.getReferenceUrl());
        dto.setActive(battery.isActive());
        dto.setCapacityAh(battery.getCapacityAh());
        dto.setVoltage(battery.getVoltage());
        dto.setChemistry(battery.getChemistry());
        dto.setCycleLife(battery.getCycleLife());
        dto.setBmsMaxDischargeA(battery.getBmsMaxDischargeA());
        dto.setIntegratedBreakerA(battery.getIntegratedBreakerA());
        dto.setTerminalSize(battery.getTerminalSize());
        dto.setCapacityKwh(battery.getCapacityKwh());
        dto.setDepthOfDischargePercent(battery.getDepthOfDischarge() * 100);
        dto.setMaxDischargeCurrentA(battery.getMaxDischargeCurrentA());
        return dto;
    }
}
