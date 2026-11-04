package com.endel.demobox.controller.catalog;

import com.endel.demobox.model.dto.catalog.AluminumStructureDto;
import com.endel.demobox.service.catalog.AluminumStructureService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/catalog/structures")
public class AluminumStructureController {
    private final AluminumStructureService service;

    public AluminumStructureController(AluminumStructureService service) {
        this.service = service;
    }

    @GetMapping
    public List<AluminumStructureDto> getAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public AluminumStructureDto getById(@PathVariable Long id) {
        return service.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AluminumStructureDto create(@Valid @RequestBody AluminumStructureDto dto) {
        return service.create(dto);
    }

    @PutMapping("/{id}")
    public AluminumStructureDto update(@PathVariable Long id, @Valid @RequestBody AluminumStructureDto dto) {
        return service.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
