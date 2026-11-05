package com.endel.demobox.controller.account;

import com.endel.demobox.service.account.AccountService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Optional;

@Controller
public class AccountPageController {
    private final AccountService accountService;

    public AccountPageController(AccountService accountService) {
        this.accountService = accountService;
    }

    /** Login/sign-up or "Mi cuenta", decided client-side from the stored customer token. */
    @GetMapping("/cuenta")
    public String account() {
        return "cuenta";
    }

    @GetMapping("/cuenta/olvide")
    public String forgotPassword() {
        return "cuenta-olvide";
    }

    /** Only shows the form; the token is consumed when the new password is submitted. */
    @GetMapping("/cuenta/restablecer")
    public String resetPassword(@RequestParam(required = false) String token, Model model) {
        model.addAttribute("token", token);
        return "cuenta-restablecer";
    }

    @GetMapping("/cuenta/verificar")
    public String verifyEmail(@RequestParam(required = false) String token, Model model) {
        Optional<Integer> claimedOrders = accountService.verifyEmail(token);
        model.addAttribute("verified", claimedOrders.isPresent());
        model.addAttribute("claimedOrders", claimedOrders.orElse(0));
        return "cuenta-verificar";
    }
}
