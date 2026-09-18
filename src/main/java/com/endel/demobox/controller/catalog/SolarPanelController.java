package com.endel.demobox.controller.catalog;

import com.endel.demobox.model.dto.catalog.SolarPanelDto;
import com.endel.demobox.service.catalog.SolarPanelService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/catalog/panels")
public class SolarPanelController {
    private final SolarPanelService service;

    public SolarPanelController(SolarPanelService service) {
        this.service = service;
    }

    @GetMapping
    public List<SolarPanelDto> getAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public SolarPanelDto getById(@PathVariable Long id) {
        return service.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SolarPanelDto create(@Valid @RequestBody SolarPanelDto dto) {
        return service.create(dto);
    }

    @PutMapping("/{id}")
    public SolarPanelDto update(@PathVariable Long id, @Valid @RequestBody SolarPanelDto dto) {
        return service.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
