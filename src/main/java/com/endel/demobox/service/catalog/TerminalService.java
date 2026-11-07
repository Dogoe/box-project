package com.endel.demobox.service.catalog;

import com.endel.demobox.exception.ResourceNotFoundException;
import com.endel.demobox.model.catalog.PhotoUrls;
import com.endel.demobox.model.catalog.Terminal;
import com.endel.demobox.model.catalog.TerminalSize;
import com.endel.demobox.model.dto.catalog.TerminalDto;
import com.endel.demobox.repository.catalog.TerminalRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TerminalService {
    private final TerminalRepository repository;

    public TerminalService(TerminalRepository repository) {
        this.repository = repository;
    }

    public List<TerminalDto> findAll() {
        return repository.findAll().stream().map(this::toDto).toList();
    }

    public TerminalDto getById(Long id) {
        return toDto(findEntity(id));
    }

    public TerminalDto create(TerminalDto dto) {
        Terminal terminal = new Terminal();
        applyDto(terminal, dto);
        return toDto(repository.save(terminal));
    }

    public TerminalDto update(Long id, TerminalDto dto) {
        Terminal terminal = findEntity(id);
        applyDto(terminal, dto);
        return toDto(repository.save(terminal));
    }

    public void delete(Long id) {
        repository.delete(findEntity(id));
    }

    private Terminal findEntity(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Terminal not found: " + id));
    }

    private void applyDto(Terminal terminal, TerminalDto dto) {
        terminal.setSku(dto.getSku());
        terminal.setName(dto.getName());
        terminal.setBrand(dto.getBrand());
        terminal.setDescription(dto.getDescription());
        terminal.setPrice(dto.getPrice());
        terminal.setStockQuantity(dto.getStockQuantity());
        terminal.setPhotos(PhotoUrls.resolve(dto.getImageUrls(), dto.getImageUrl()));
        terminal.setReferenceUrl(dto.getReferenceUrl());
        terminal.setActive(dto.isActive());
        terminal.setGauge(dto.getGauge());
        terminal.setTerminalSize(TerminalSize.orDefault(dto.getTerminalSize()));
        terminal.setPiecesPerPack(dto.getPiecesPerPack() == null ? 1 : dto.getPiecesPerPack());
    }

    private TerminalDto toDto(Terminal terminal) {
        TerminalDto dto = new TerminalDto();
        dto.setId(terminal.getId());
        dto.setSku(terminal.getSku());
        dto.setCode(terminal.getCode());
        dto.setName(terminal.getName());
        dto.setBrand(terminal.getBrand());
        dto.setDescription(terminal.getDescription());
        dto.setPrice(terminal.getPrice());
        dto.setStockQuantity(terminal.getStockQuantity());
        dto.setImageUrls(terminal.getPhotos());
        dto.setImageUrl(terminal.getPhotos().isEmpty() ? null : terminal.getPhotos().get(0));
        dto.setReferenceUrl(terminal.getReferenceUrl());
        dto.setActive(terminal.isActive());
        dto.setGauge(terminal.getGauge());
        dto.setTerminalSize(terminal.getTerminalSize());
        dto.setPiecesPerPack(terminal.getPiecesPerPack());
        dto.setSpecLabel(terminal.getSpecLabel());
        return dto;
    }
}
