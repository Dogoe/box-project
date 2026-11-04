package com.endel.demobox.controller.catalog;

import com.endel.demobox.model.dto.catalog.ConnectionCableDto;
import com.endel.demobox.service.catalog.ConnectionCableService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/catalog/cables")
public class ConnectionCableController {
    private final ConnectionCableService service;

    public ConnectionCableController(ConnectionCableService service) {
        this.service = service;
    }

    @GetMapping
    public List<ConnectionCableDto> getAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public ConnectionCableDto getById(@PathVariable Long id) {
        return service.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ConnectionCableDto create(@Valid @RequestBody ConnectionCableDto dto) {
        return service.create(dto);
    }

    @PutMapping("/{id}")
    public ConnectionCableDto update(@PathVariable Long id, @Valid @RequestBody ConnectionCableDto dto) {
        return service.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
