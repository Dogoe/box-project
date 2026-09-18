package com.endel.demobox.quote.dto;

import com.endel.demobox.quote.SystemType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.util.List;

@Data
public class LoadSurveyDto {

    @NotEmpty
    @Valid
    private List<ApplianceLoadDto> appliances;

    @NotNull
    private SystemType systemType;

    @Positive
    private double autonomyDays = 1;

    @Positive
    private double peakSunHours;

    /** Usable fraction of battery capacity before it's considered discharged, e.g. 0.8 for lithium-ion. */
    @DecimalMin("0.1")
    @DecimalMax("1.0")
    private double depthOfDischarge = 0.8;

    @DecimalMin("0.1")
    @DecimalMax("1.0")
    private double batteryRoundTripEfficiency = 0.95;

    /** Accounts for wiring, temperature and inverter losses between panel nameplate output and usable energy. */
    @DecimalMin("0.1")
    @DecimalMax("1.0")
    private double systemDerateFactor = 0.80;

    @DecimalMin("1.0")
    private double inverterSafetyMargin = 1.10;
}
