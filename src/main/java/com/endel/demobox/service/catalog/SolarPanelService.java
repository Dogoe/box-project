package com.endel.demobox.service.catalog;

import com.endel.demobox.exception.ResourceNotFoundException;
import com.endel.demobox.model.catalog.SolarPanel;
import com.endel.demobox.model.dto.catalog.SolarPanelDto;
import com.endel.demobox.repository.catalog.SolarPanelRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SolarPanelService {
    private final SolarPanelRepository repository;

    public SolarPanelService(SolarPanelRepository repository) {
        this.repository = repository;
    }

    public List<SolarPanelDto> findAll() {
        return repository.findAll().stream().map(this::toDto).toList();
    }

    public SolarPanelDto getById(Long id) {
        return toDto(findEntity(id));
    }

    public SolarPanelDto create(SolarPanelDto dto) {
        SolarPanel panel = new SolarPanel();
        applyDto(panel, dto);
        return toDto(repository.save(panel));
    }

    public SolarPanelDto update(Long id, SolarPanelDto dto) {
        SolarPanel panel = findEntity(id);
        applyDto(panel, dto);
        return toDto(repository.save(panel));
    }

    public void delete(Long id) {
        repository.delete(findEntity(id));
    }

    private SolarPanel findEntity(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Solar panel not found: " + id));
    }

    private void applyDto(SolarPanel panel, SolarPanelDto dto) {
        panel.setSku(dto.getSku());
        panel.setName(dto.getName());
        panel.setBrand(dto.getBrand());
        panel.setDescription(dto.getDescription());
        panel.setPrice(dto.getPrice());
        panel.setStockQuantity(dto.getStockQuantity());
        panel.setImageUrl(dto.getImageUrl());
        panel.setActive(dto.isActive());
        panel.setWattage(dto.getWattage());
        panel.setTechnology(dto.getTechnology());
        panel.setEfficiencyPercent(dto.getEfficiencyPercent());
        panel.setVoc(dto.getVoc());
        panel.setIsc(dto.getIsc());
    }

    private SolarPanelDto toDto(SolarPanel panel) {
        SolarPanelDto dto = new SolarPanelDto();
        dto.setId(panel.getId());
        dto.setSku(panel.getSku());
        dto.setName(panel.getName());
        dto.setBrand(panel.getBrand());
        dto.setDescription(panel.getDescription());
        dto.setPrice(panel.getPrice());
        dto.setStockQuantity(panel.getStockQuantity());
        dto.setImageUrl(panel.getImageUrl());
        dto.setActive(panel.isActive());
        dto.setWattage(panel.getWattage());
        dto.setTechnology(panel.getTechnology());
        dto.setEfficiencyPercent(panel.getEfficiencyPercent());
        dto.setVoc(panel.getVoc());
        dto.setIsc(panel.getIsc());
        return dto;
    }
}
