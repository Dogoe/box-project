package com.endel.demobox.controller.catalog;

import com.endel.demobox.model.dto.catalog.TerminalDto;
import com.endel.demobox.service.catalog.TerminalService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/catalog/terminals")
public class TerminalController {
    private final TerminalService service;

    public TerminalController(TerminalService service) {
        this.service = service;
    }

    @GetMapping
    public List<TerminalDto> getAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public TerminalDto getById(@PathVariable Long id) {
        return service.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TerminalDto create(@Valid @RequestBody TerminalDto dto) {
        return service.create(dto);
    }

    @PutMapping("/{id}")
    public TerminalDto update(@PathVariable Long id, @Valid @RequestBody TerminalDto dto) {
        return service.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
