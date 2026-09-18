package com.endel.demobox.quote;

import com.endel.demobox.quote.dto.ApplianceLoadDto;
import com.endel.demobox.quote.dto.LoadSurveyDto;
import com.endel.demobox.quote.dto.SizingResultDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SizingCalculatorServiceTest {

    private final SizingCalculatorService service = new SizingCalculatorService();

    // Fridge:   150W, 24h/day, motor load, 3.0x surge   -> 3.6 kWh/day,  surge contribution 0.30 kW
    // Lights:   10 bulbs x 10W, 6h/day                  -> 0.6 kWh/day
    // TV:       100W, 4h/day, NOT simultaneous           -> 0.4 kWh/day, excluded from continuous load
    // Pump:     750W, 1h/day, motor load, 2.5x surge     -> 0.75 kWh/day, surge contribution 1.125 kW (largest)
    private LoadSurveyDto baseSurvey(SystemType systemType) {
        ApplianceLoadDto fridge = new ApplianceLoadDto();
        fridge.setName("Fridge");
        fridge.setWatts(150);
        fridge.setHoursPerDay(24);
        fridge.setQuantity(1);
        fridge.setRunsSimultaneously(true);
        fridge.setMotorLoad(true);
        fridge.setSurgeMultiplier(3.0);

        ApplianceLoadDto lights = new ApplianceLoadDto();
        lights.setName("Lights");
        lights.setWatts(10);
        lights.setHoursPerDay(6);
        lights.setQuantity(10);
        lights.setRunsSimultaneously(true);

        ApplianceLoadDto tv = new ApplianceLoadDto();
        tv.setName("TV");
        tv.setWatts(100);
        tv.setHoursPerDay(4);
        tv.setQuantity(1);
        tv.setRunsSimultaneously(false);

        ApplianceLoadDto pump = new ApplianceLoadDto();
        pump.setName("Water pump");
        pump.setWatts(750);
        pump.setHoursPerDay(1);
        pump.setQuantity(1);
        pump.setRunsSimultaneously(true);
        pump.setMotorLoad(true);
        pump.setSurgeMultiplier(2.5);

        LoadSurveyDto survey = new LoadSurveyDto();
        survey.setAppliances(List.of(fridge, lights, tv, pump));
        survey.setSystemType(systemType);
        survey.setAutonomyDays(2);
        survey.setPeakSunHours(5.0);
        return survey;
    }

    @Test
    void offGridSizing_matchesHandCalculatedValues() {
        SizingResultDto result = service.calculate(baseSurvey(SystemType.OFF_GRID));

        assertEquals(5.35, result.getDailyConsumptionKwh(), 0.001);
        assertEquals(1.0, result.getContinuousLoadKw(), 0.001);
        // largest motor surge is the pump's 1.125 kW, not fridge's 0.30 kW and not their sum
        assertEquals(2.13, result.getPeakLoadKw(), 0.001); // 2.125 kW, rounded
        assertEquals(2.34, result.getRecommendedInverterKw(), 0.001); // 2.125 * 1.10, rounded
        assertEquals(14.08, result.getRecommendedBatteryCapacityKwh(), 0.01); // (5.35*2)/(0.8*0.95)
        assertEquals(1.34, result.getRecommendedPanelArrayKwp(), 0.01); // 5.35/(5.0*0.80)
    }

    @Test
    void onGridSizing_skipsBatteryCapacityButStillSizesPanelsAndInverter() {
        SizingResultDto result = service.calculate(baseSurvey(SystemType.ON_GRID));

        assertEquals(0.0, result.getRecommendedBatteryCapacityKwh());
        assertEquals(1.34, result.getRecommendedPanelArrayKwp(), 0.01);
        assertEquals(2.34, result.getRecommendedInverterKw(), 0.001);
    }

    @Test
    void noMotorLoads_peakLoadEqualsContinuousLoad() {
        ApplianceLoadDto lights = new ApplianceLoadDto();
        lights.setName("Lights");
        lights.setWatts(20);
        lights.setHoursPerDay(5);
        lights.setQuantity(4);

        LoadSurveyDto survey = new LoadSurveyDto();
        survey.setAppliances(List.of(lights));
        survey.setSystemType(SystemType.HYBRID);
        survey.setAutonomyDays(1);
        survey.setPeakSunHours(4.5);

        SizingResultDto result = service.calculate(survey);

        assertEquals(result.getContinuousLoadKw(), result.getPeakLoadKw(), 0.0001);
    }
}
