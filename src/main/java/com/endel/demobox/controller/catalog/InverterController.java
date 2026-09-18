package com.endel.demobox.controller.catalog;

import com.endel.demobox.model.dto.catalog.InverterDto;
import com.endel.demobox.service.catalog.InverterService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/catalog/inverters")
public class InverterController {
    private final InverterService service;

    public InverterController(InverterService service) {
        this.service = service;
    }

    @GetMapping
    public List<InverterDto> getAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public InverterDto getById(@PathVariable Long id) {
        return service.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InverterDto create(@Valid @RequestBody InverterDto dto) {
        return service.create(dto);
    }

    @PutMapping("/{id}")
    public InverterDto update(@PathVariable Long id, @Valid @RequestBody InverterDto dto) {
        return service.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
