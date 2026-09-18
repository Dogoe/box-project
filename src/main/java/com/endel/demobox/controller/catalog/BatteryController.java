package com.endel.demobox.controller.catalog;

import com.endel.demobox.model.dto.catalog.BatteryDto;
import com.endel.demobox.service.catalog.BatteryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/catalog/batteries")
public class BatteryController {
    private final BatteryService service;

    public BatteryController(BatteryService service) {
        this.service = service;
    }

    @GetMapping
    public List<BatteryDto> getAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public BatteryDto getById(@PathVariable Long id) {
        return service.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BatteryDto create(@Valid @RequestBody BatteryDto dto) {
        return service.create(dto);
    }

    @PutMapping("/{id}")
    public BatteryDto update(@PathVariable Long id, @Valid @RequestBody BatteryDto dto) {
        return service.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
