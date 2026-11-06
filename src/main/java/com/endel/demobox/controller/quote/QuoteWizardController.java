package com.endel.demobox.controller.quote;

import com.endel.demobox.quote.SolarZone;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
public class QuoteWizardController {

    /** Days the system should run without sun; 1 is the default and 1-2 the usual choice. */
    static final List<Integer> AUTONOMY_DAYS = List.of(1, 2, 3, 4);

    @GetMapping("/cotizacion")
    public String cotizacion(Model model) {
        model.addAttribute("zones", SolarZone.ALL);
        model.addAttribute("autonomyDays", AUTONOMY_DAYS);
        return "cotizacion";
    }
}
