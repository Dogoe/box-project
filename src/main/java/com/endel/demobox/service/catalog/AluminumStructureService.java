package com.endel.demobox.service.catalog;

import com.endel.demobox.model.catalog.PhotoUrls;
import com.endel.demobox.exception.ResourceNotFoundException;
import com.endel.demobox.model.catalog.AluminumStructure;
import com.endel.demobox.model.dto.catalog.AluminumStructureDto;
import com.endel.demobox.repository.catalog.AluminumStructureRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AluminumStructureService {
    private final AluminumStructureRepository repository;

    public AluminumStructureService(AluminumStructureRepository repository) {
        this.repository = repository;
    }

    public List<AluminumStructureDto> findAll() {
        return repository.findAll().stream().map(this::toDto).toList();
    }

    public List<AluminumStructureDto> findAllActive() {
        return repository.findAll().stream()
                .filter(AluminumStructure::isActive)
                .map(this::toDto)
                .toList();
    }

    public AluminumStructureDto getById(Long id) {
        return toDto(findEntity(id));
    }

    public AluminumStructureDto create(AluminumStructureDto dto) {
        AluminumStructure structure = new AluminumStructure();
        applyDto(structure, dto);
        return toDto(repository.save(structure));
    }

    public AluminumStructureDto update(Long id, AluminumStructureDto dto) {
        AluminumStructure structure = findEntity(id);
        applyDto(structure, dto);
        return toDto(repository.save(structure));
    }

    public void delete(Long id) {
        repository.delete(findEntity(id));
    }

    private AluminumStructure findEntity(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Aluminum structure not found: " + id));
    }

    private void applyDto(AluminumStructure structure, AluminumStructureDto dto) {
        structure.setSku(dto.getSku());
        structure.setName(dto.getName());
        structure.setBrand(dto.getBrand());
        structure.setDescription(dto.getDescription());
        structure.setPrice(dto.getPrice());
        structure.setStockQuantity(dto.getStockQuantity());
        structure.setPhotos(PhotoUrls.resolve(dto.getImageUrls(), dto.getImageUrl()));
        structure.setReferenceUrl(dto.getReferenceUrl());
        structure.setActive(dto.isActive());
    }

    private AluminumStructureDto toDto(AluminumStructure structure) {
        AluminumStructureDto dto = new AluminumStructureDto();
        dto.setId(structure.getId());
        dto.setSku(structure.getSku());
        dto.setName(structure.getName());
        dto.setBrand(structure.getBrand());
        dto.setDescription(structure.getDescription());
        dto.setPrice(structure.getPrice());
        dto.setStockQuantity(structure.getStockQuantity());
        dto.setImageUrls(structure.getPhotos());
        dto.setImageUrl(structure.getPhotos().isEmpty() ? null : structure.getPhotos().get(0));
        dto.setReferenceUrl(structure.getReferenceUrl());
        dto.setActive(structure.isActive());
        return dto;
    }
}
