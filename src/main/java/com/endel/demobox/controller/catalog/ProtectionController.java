package com.endel.demobox.controller.catalog;

import com.endel.demobox.model.dto.catalog.ProtectionDto;
import com.endel.demobox.service.catalog.ProtectionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/catalog/protections")
public class ProtectionController {
    private final ProtectionService service;

    public ProtectionController(ProtectionService service) {
        this.service = service;
    }

    @GetMapping
    public List<ProtectionDto> getAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public ProtectionDto getById(@PathVariable Long id) {
        return service.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProtectionDto create(@Valid @RequestBody ProtectionDto dto) {
        return service.create(dto);
    }

    @PutMapping("/{id}")
    public ProtectionDto update(@PathVariable Long id, @Valid @RequestBody ProtectionDto dto) {
        return service.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
