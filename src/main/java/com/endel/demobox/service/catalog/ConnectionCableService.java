package com.endel.demobox.service.catalog;

import com.endel.demobox.exception.ResourceNotFoundException;
import com.endel.demobox.model.catalog.ConnectionCable;
import com.endel.demobox.model.dto.catalog.ConnectionCableDto;
import com.endel.demobox.repository.catalog.ConnectionCableRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ConnectionCableService {
    private final ConnectionCableRepository repository;

    public ConnectionCableService(ConnectionCableRepository repository) {
        this.repository = repository;
    }

    public List<ConnectionCableDto> findAll() {
        return repository.findAll().stream().map(this::toDto).toList();
    }

    public List<ConnectionCableDto> findAllActive() {
        return repository.findAll().stream()
                .filter(ConnectionCable::isActive)
                .map(this::toDto)
                .toList();
    }

    public ConnectionCableDto getById(Long id) {
        return toDto(findEntity(id));
    }

    public ConnectionCableDto create(ConnectionCableDto dto) {
        ConnectionCable cable = new ConnectionCable();
        applyDto(cable, dto);
        return toDto(repository.save(cable));
    }

    public ConnectionCableDto update(Long id, ConnectionCableDto dto) {
        ConnectionCable cable = findEntity(id);
        applyDto(cable, dto);
        return toDto(repository.save(cable));
    }

    public void delete(Long id) {
        repository.delete(findEntity(id));
    }

    private ConnectionCable findEntity(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Connection cable not found: " + id));
    }

    private void applyDto(ConnectionCable cable, ConnectionCableDto dto) {
        cable.setSku(dto.getSku());
        cable.setName(dto.getName());
        cable.setBrand(dto.getBrand());
        cable.setDescription(dto.getDescription());
        cable.setPrice(dto.getPrice());
        cable.setStockQuantity(dto.getStockQuantity());
        cable.setImageUrl(dto.getImageUrl());
        cable.setActive(dto.isActive());
        cable.setLengthMeters(dto.getLengthMeters());
    }

    private ConnectionCableDto toDto(ConnectionCable cable) {
        ConnectionCableDto dto = new ConnectionCableDto();
        dto.setId(cable.getId());
        dto.setSku(cable.getSku());
        dto.setName(cable.getName());
        dto.setBrand(cable.getBrand());
        dto.setDescription(cable.getDescription());
        dto.setPrice(cable.getPrice());
        dto.setStockQuantity(cable.getStockQuantity());
        dto.setImageUrl(cable.getImageUrl());
        dto.setActive(cable.isActive());
        dto.setLengthMeters(cable.getLengthMeters());
        return dto;
    }
}
