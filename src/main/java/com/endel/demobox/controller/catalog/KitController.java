package com.endel.demobox.controller.catalog;

import com.endel.demobox.model.dto.catalog.KitDto;
import com.endel.demobox.service.catalog.KitService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/catalog/kits")
public class KitController {
    private final KitService service;

    public KitController(KitService service) {
        this.service = service;
    }

    @GetMapping
    public List<KitDto> getAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public KitDto getById(@PathVariable Long id) {
        return service.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public KitDto create(@Valid @RequestBody KitDto dto) {
        return service.create(dto);
    }

    @PutMapping("/{id}")
    public KitDto update(@PathVariable Long id, @Valid @RequestBody KitDto dto) {
        return service.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
