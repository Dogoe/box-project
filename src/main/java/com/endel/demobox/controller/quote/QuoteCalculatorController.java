package com.endel.demobox.controller.quote;

import com.endel.demobox.quote.SizingCalculatorService;
import com.endel.demobox.quote.dto.LoadSurveyDto;
import com.endel.demobox.quote.dto.SizingResultDto;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/quotes")
public class QuoteCalculatorController {
    private final SizingCalculatorService sizingCalculatorService;

    public QuoteCalculatorController(SizingCalculatorService sizingCalculatorService) {
        this.sizingCalculatorService = sizingCalculatorService;
    }

    @PostMapping("/calculate")
    public SizingResultDto calculate(@Valid @RequestBody LoadSurveyDto survey) {
        return sizingCalculatorService.calculate(survey);
    }
}
