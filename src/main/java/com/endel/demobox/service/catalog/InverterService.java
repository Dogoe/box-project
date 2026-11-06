package com.endel.demobox.service.catalog;

import com.endel.demobox.model.catalog.PhotoUrls;
import com.endel.demobox.model.catalog.TerminalSize;
import com.endel.demobox.exception.ResourceNotFoundException;
import com.endel.demobox.model.catalog.Inverter;
import com.endel.demobox.model.dto.catalog.InverterDto;
import com.endel.demobox.repository.catalog.InverterRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class InverterService {
    private final InverterRepository repository;

    public InverterService(InverterRepository repository) {
        this.repository = repository;
    }

    public List<InverterDto> findAll() {
        return repository.findAll().stream().map(this::toDto).toList();
    }

    public List<InverterDto> findAllActive() {
        return repository.findAll().stream()
                .filter(Inverter::isActive)
                .map(this::toDto)
                .toList();
    }

    public InverterDto getById(Long id) {
        return toDto(findEntity(id));
    }

    public InverterDto create(InverterDto dto) {
        Inverter inverter = new Inverter();
        applyDto(inverter, dto);
        return toDto(repository.save(inverter));
    }

    public InverterDto update(Long id, InverterDto dto) {
        Inverter inverter = findEntity(id);
        applyDto(inverter, dto);
        return toDto(repository.save(inverter));
    }

    public void delete(Long id) {
        repository.delete(findEntity(id));
    }

    private Inverter findEntity(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inverter not found: " + id));
    }

    private void applyDto(Inverter inverter, InverterDto dto) {
        inverter.setSku(dto.getSku());
        inverter.setName(dto.getName());
        inverter.setBrand(dto.getBrand());
        inverter.setDescription(dto.getDescription());
        inverter.setPrice(dto.getPrice());
        inverter.setStockQuantity(dto.getStockQuantity());
        inverter.setPhotos(PhotoUrls.resolve(dto.getImageUrls(), dto.getImageUrl()));
        inverter.setReferenceUrl(dto.getReferenceUrl());
        inverter.setActive(dto.isActive());
        inverter.setRatedPowerKw(dto.getRatedPowerKw());
        inverter.setSurgePowerKw(dto.getSurgePowerKw());
        inverter.setInverterType(dto.getInverterType());
        inverter.setBatteryVoltage(dto.getBatteryVoltage());
        inverter.setTerminalSize(TerminalSize.orDefault(dto.getTerminalSize()));
        boolean builtInCharger = !Boolean.FALSE.equals(dto.getBuiltInCharger());
        inverter.setBuiltInCharger(builtInCharger);
        if (builtInCharger) {
            inverter.setMpptChannels(dto.getMpptChannels());
            inverter.setMaxInputVoltage(dto.getMaxInputVoltage());
            inverter.setMpptMinVoltage(dto.getMpptMinVoltage());
            inverter.setMpptMaxChargeCurrentA(dto.getMpptMaxChargeCurrentA());
            inverter.setMpptMaxInputCurrentA(dto.getMpptMaxInputCurrentA());
            inverter.setMaxPvInputPowerW(dto.getMaxPvInputPowerW());
        } else {
            // No solar charger: the kit's charge controller provides these, so don't keep stale values.
            inverter.setMpptChannels(null);
            inverter.setMaxInputVoltage(0);
            inverter.setMpptMinVoltage(null);
            inverter.setMpptMaxChargeCurrentA(null);
            inverter.setMpptMaxInputCurrentA(null);
            inverter.setMaxPvInputPowerW(null);
        }
    }

    private InverterDto toDto(Inverter inverter) {
        InverterDto dto = new InverterDto();
        dto.setId(inverter.getId());
        dto.setSku(inverter.getSku());
        dto.setName(inverter.getName());
        dto.setBrand(inverter.getBrand());
        dto.setDescription(inverter.getDescription());
        dto.setPrice(inverter.getPrice());
        dto.setStockQuantity(inverter.getStockQuantity());
        dto.setImageUrls(inverter.getPhotos());
        dto.setImageUrl(inverter.getPhotos().isEmpty() ? null : inverter.getPhotos().get(0));
        dto.setReferenceUrl(inverter.getReferenceUrl());
        dto.setActive(inverter.isActive());
        dto.setRatedPowerKw(inverter.getRatedPowerKw());
        dto.setSurgePowerKw(inverter.getSurgePowerKw());
        dto.setInverterType(inverter.getInverterType());
        dto.setBuiltInCharger(inverter.hasBuiltInCharger());
        dto.setMpptChannels(inverter.getMpptChannels());
        dto.setMaxInputVoltage(inverter.getMaxInputVoltage());
        dto.setBatteryVoltage(inverter.getBatteryVoltage());
        dto.setMpptMinVoltage(inverter.getMpptMinVoltage());
        dto.setMpptMaxChargeCurrentA(inverter.getMpptMaxChargeCurrentA());
        dto.setMpptMaxInputCurrentA(inverter.getMpptMaxInputCurrentA());
        dto.setMaxPvInputPowerW(inverter.getMaxPvInputPowerW());
        dto.setTerminalSize(inverter.getTerminalSize());
        dto.setMaxChargePowerW(inverter.getMaxChargePowerW());
        dto.setEffectiveMaxPvPowerW(inverter.getEffectiveMaxPvPowerW());
        dto.setMaxPvPowerEstimated(inverter.isMaxPvPowerEstimated());
        return dto;
    }
}
