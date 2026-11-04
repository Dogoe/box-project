package com.endel.demobox.controller;

import com.endel.demobox.model.dto.WiringSettingsDto;
import com.endel.demobox.service.WiringSettingsService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings/wiring")
public class WiringSettingsController {
    private final WiringSettingsService service;

    public WiringSettingsController(WiringSettingsService service) {
        this.service = service;
    }

    @GetMapping
    public WiringSettingsDto get() {
        return service.getCurrent();
    }

    @PutMapping
    public WiringSettingsDto update(@Valid @RequestBody WiringSettingsDto dto) {
        return service.update(dto);
    }
}
