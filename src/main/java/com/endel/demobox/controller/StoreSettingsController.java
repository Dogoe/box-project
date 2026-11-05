package com.endel.demobox.controller;

import com.endel.demobox.model.dto.StoreSettingsDto;
import com.endel.demobox.service.StoreSettingsService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings/store")
public class StoreSettingsController {
    private final StoreSettingsService service;

    public StoreSettingsController(StoreSettingsService service) {
        this.service = service;
    }

    @GetMapping
    public StoreSettingsDto get() {
        return service.getCurrent();
    }

    @PutMapping
    public StoreSettingsDto update(@Valid @RequestBody StoreSettingsDto dto) {
        return service.update(dto);
    }
}
