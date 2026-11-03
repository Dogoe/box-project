package com.endel.demobox.controller;

import com.endel.demobox.model.dto.catalog.KitCapacityDto;
import com.endel.demobox.model.dto.catalog.KitDto;
import com.endel.demobox.service.catalog.KitCapacityEstimator;
import com.endel.demobox.service.catalog.KitService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
public class KitsPageController {

    private final KitService kitService;
    private final KitCapacityEstimator kitCapacityEstimator;

    public KitsPageController(KitService kitService, KitCapacityEstimator kitCapacityEstimator) {
        this.kitService = kitService;
        this.kitCapacityEstimator = kitCapacityEstimator;
    }

    @GetMapping("/kits")
    public String kits(Model model) {
        List<KitDto> kits = kitService.findAllActive();
        Map<Long, KitCapacityDto> capacityByKitId = kits.stream()
                .collect(Collectors.toMap(KitDto::getId, kitCapacityEstimator::estimate));

        model.addAttribute("kits", kits);
        model.addAttribute("capacityByKitId", capacityByKitId);
        return "kits";
    }
}
