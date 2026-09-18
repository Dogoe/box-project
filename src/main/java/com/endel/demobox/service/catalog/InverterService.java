package com.endel.demobox.service.catalog;

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
        inverter.setImageUrl(dto.getImageUrl());
        inverter.setActive(dto.isActive());
        inverter.setRatedPowerKw(dto.getRatedPowerKw());
        inverter.setSurgePowerKw(dto.getSurgePowerKw());
        inverter.setInverterType(dto.getInverterType());
        inverter.setMpptChannels(dto.getMpptChannels());
        inverter.setMaxInputVoltage(dto.getMaxInputVoltage());
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
        dto.setImageUrl(inverter.getImageUrl());
        dto.setActive(inverter.isActive());
        dto.setRatedPowerKw(inverter.getRatedPowerKw());
        dto.setSurgePowerKw(inverter.getSurgePowerKw());
        dto.setInverterType(inverter.getInverterType());
        dto.setMpptChannels(inverter.getMpptChannels());
        dto.setMaxInputVoltage(inverter.getMaxInputVoltage());
        return dto;
    }
}
