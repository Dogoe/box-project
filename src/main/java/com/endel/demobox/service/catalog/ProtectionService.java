package com.endel.demobox.service.catalog;

import com.endel.demobox.exception.BadRequestException;
import com.endel.demobox.exception.ResourceNotFoundException;
import com.endel.demobox.model.catalog.PhotoUrls;
import com.endel.demobox.model.catalog.TerminalSize;
import com.endel.demobox.model.catalog.Protection;
import com.endel.demobox.model.catalog.ProtectionType;
import com.endel.demobox.model.dto.catalog.ProtectionDto;
import com.endel.demobox.repository.catalog.ProtectionRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProtectionService {
    private final ProtectionRepository repository;

    public ProtectionService(ProtectionRepository repository) {
        this.repository = repository;
    }

    public List<ProtectionDto> findAll() {
        return repository.findAll().stream().map(this::toDto).toList();
    }

    public ProtectionDto getById(Long id) {
        return toDto(findEntity(id));
    }

    public ProtectionDto create(ProtectionDto dto) {
        Protection protection = new Protection();
        applyDto(protection, dto);
        return toDto(repository.save(protection));
    }

    public ProtectionDto update(Long id, ProtectionDto dto) {
        Protection protection = findEntity(id);
        applyDto(protection, dto);
        return toDto(repository.save(protection));
    }

    public void delete(Long id) {
        repository.delete(findEntity(id));
    }

    private Protection findEntity(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Protection not found: " + id));
    }

    private void applyDto(Protection protection, ProtectionDto dto) {
        if (dto.getProtectionType() == ProtectionType.FUSE && dto.getFuseClass() == null) {
            throw new BadRequestException("Elige la clase del fusible (MRBF, ANL, Clase T, gPV u otro).");
        }
        protection.setSku(dto.getSku());
        protection.setName(dto.getName());
        protection.setBrand(dto.getBrand());
        protection.setDescription(dto.getDescription());
        protection.setPrice(dto.getPrice());
        protection.setStockQuantity(dto.getStockQuantity());
        protection.setPhotos(PhotoUrls.resolve(dto.getImageUrls(), dto.getImageUrl()));
        protection.setReferenceUrl(dto.getReferenceUrl());
        protection.setActive(dto.isActive());
        protection.setProtectionType(dto.getProtectionType());
        protection.setFuseClass(dto.getProtectionType() == ProtectionType.FUSE ? dto.getFuseClass() : null);
        protection.setRatedCurrentA(dto.getRatedCurrentA());
        protection.setMaxVoltage(dto.getMaxVoltage());
        protection.setPoles(dto.getPoles());
        protection.setTerminalSize(TerminalSize.orDefault(dto.getTerminalSize()));
    }

    private ProtectionDto toDto(Protection protection) {
        ProtectionDto dto = new ProtectionDto();
        dto.setId(protection.getId());
        dto.setSku(protection.getSku());
        dto.setCode(protection.getCode());
        dto.setName(protection.getName());
        dto.setBrand(protection.getBrand());
        dto.setDescription(protection.getDescription());
        dto.setPrice(protection.getPrice());
        dto.setStockQuantity(protection.getStockQuantity());
        dto.setImageUrls(protection.getPhotos());
        dto.setImageUrl(protection.getPhotos().isEmpty() ? null : protection.getPhotos().get(0));
        dto.setReferenceUrl(protection.getReferenceUrl());
        dto.setActive(protection.isActive());
        dto.setProtectionType(protection.getProtectionType());
        dto.setFuseClass(protection.getFuseClass());
        dto.setRatedCurrentA(protection.getRatedCurrentA());
        dto.setMaxVoltage(protection.getMaxVoltage());
        dto.setPoles(protection.getPoles());
        dto.setTerminalSize(protection.getTerminalSize());
        dto.setSpecLabel(protection.getSpecLabel());
        return dto;
    }
}
