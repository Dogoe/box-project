package com.endel.demobox.controller;

import com.endel.demobox.service.catalog.BatteryService;
import com.endel.demobox.service.catalog.InverterService;
import com.endel.demobox.service.catalog.KitService;
import com.endel.demobox.service.catalog.SolarPanelService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    private final SolarPanelService solarPanelService;
    private final BatteryService batteryService;
    private final InverterService inverterService;
    private final KitService kitService;

    public HomeController(SolarPanelService solarPanelService,
                           BatteryService batteryService,
                           InverterService inverterService,
                           KitService kitService) {
        this.solarPanelService = solarPanelService;
        this.batteryService = batteryService;
        this.inverterService = inverterService;
        this.kitService = kitService;
    }

    @GetMapping("/home")
    public String home(Model model) {
        model.addAttribute("panels", solarPanelService.findAllActive());
        model.addAttribute("batteries", batteryService.findAllActive());
        model.addAttribute("inverters", inverterService.findAllActive());
        model.addAttribute("kits", kitService.findAllActive());
        return "home";
    }
}
