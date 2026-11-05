package com.endel.demobox.controller.catalog;

import com.endel.demobox.model.dto.catalog.ChargeControllerDto;
import com.endel.demobox.service.catalog.ChargeControllerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/catalog/charge-controllers")
public class ChargeControllerController {
    private final ChargeControllerService service;

    public ChargeControllerController(ChargeControllerService service) {
        this.service = service;
    }

    @GetMapping
    public List<ChargeControllerDto> getAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public ChargeControllerDto getById(@PathVariable Long id) {
        return service.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ChargeControllerDto create(@Valid @RequestBody ChargeControllerDto dto) {
        return service.create(dto);
    }

    @PutMapping("/{id}")
    public ChargeControllerDto update(@PathVariable Long id, @Valid @RequestBody ChargeControllerDto dto) {
        return service.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
