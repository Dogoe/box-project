package com.endel.demobox.controller.admin;

import com.endel.demobox.storage.ImageUploadService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/** Photo uploads from the admin forms (admin-only like the rest of /api/admin/**). */
@RestController
@RequestMapping("/api/admin/images")
public class AdminImageController {
    private final ImageUploadService uploadService;

    public AdminImageController(ImageUploadService uploadService) {
        this.uploadService = uploadService;
    }

    /** Lets the admin hide the upload button when storage isn't set up. */
    @GetMapping("/status")
    public Map<String, Boolean> status() {
        return Map.of("enabled", uploadService.isEnabled());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> upload(@RequestParam("file") MultipartFile file,
                                      @RequestParam(value = "folder", defaultValue = "products") String folder) throws IOException {
        return Map.of("url", uploadService.upload(folder, file.getBytes()));
    }
}
