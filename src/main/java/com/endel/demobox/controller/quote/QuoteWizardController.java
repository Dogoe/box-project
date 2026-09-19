package com.endel.demobox.controller.quote;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class QuoteWizardController {

    @GetMapping("/cotizacion")
    public String cotizacion() {
        return "cotizacion";
    }
}
