package com.endel.demobox.controller;

import com.endel.demobox.service.catalog.KitService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    private final KitService kitService;

    public HomeController(KitService kitService) {
        this.kitService = kitService;
    }

    @GetMapping("/home")
    public String home(Model model) {
        model.addAttribute("kits", kitService.findAllActive());
        return "home";
    }
}
