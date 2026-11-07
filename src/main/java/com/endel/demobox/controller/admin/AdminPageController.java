package com.endel.demobox.controller.admin;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class AdminPageController {

    @GetMapping("/admin/login")
    public String login() {
        return "admin/login";
    }

    @GetMapping("/admin")
    public String dashboard() {
        return "admin/index";
    }

    /** Page shell only; its script loads the diagram from /api/admin/kits/{id}/diagram with the admin's token. */
    @GetMapping("/admin/kits/{id}/diagrama")
    public String kitDiagram(@PathVariable Long id, Model model) {
        model.addAttribute("kitId", id);
        return "admin/kit-diagram";
    }
}
