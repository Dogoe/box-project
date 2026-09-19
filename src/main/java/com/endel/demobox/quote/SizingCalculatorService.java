package com.endel.demobox.quote;

import com.endel.demobox.quote.dto.ApplianceLoadDto;
import com.endel.demobox.quote.dto.LoadSurveyDto;
import com.endel.demobox.quote.dto.SizingResultDto;
import org.springframework.stereotype.Service;

@Service
public class SizingCalculatorService {

    public SizingResultDto calculate(LoadSurveyDto survey) {
        double dailyConsumptionKwh = survey.getAppliances().stream()
                .mapToDouble(a -> a.getWatts() * a.getHoursPerDay() * a.getQuantity() / 1000.0)
                .sum();

        double continuousLoadKw = survey.getAppliances().stream()
                .filter(ApplianceLoadDto::isRunsSimultaneously)
                .mapToDouble(a -> a.getWatts() * a.getQuantity() / 1000.0)
                .sum();

        // Only the single largest motor start-up surge is added: independent compressor
        // cycles rarely start at exactly the same instant, so summing every motor load
        // would over-size the inverter.
        double largestMotorSurgeKw = survey.getAppliances().stream()
                .filter(ApplianceLoadDto::isMotorLoad)
                .mapToDouble(a -> a.getWatts() * a.getQuantity() / 1000.0 * (a.getSurgeMultiplier() - 1))
                .max()
                .orElse(0.0);

        double peakLoadKw = continuousLoadKw + largestMotorSurgeKw;
        double recommendedInverterKw = peakLoadKw * survey.getInverterSafetyMargin();

        double recommendedBatteryCapacityKwh = survey.getSystemType() == SystemType.ON_GRID
                ? 0.0
                : (dailyConsumptionKwh * survey.getAutonomyDays())
                        / (survey.getDepthOfDischarge() * survey.getBatteryRoundTripEfficiency());

        double recommendedPanelArrayKwp = dailyConsumptionKwh
                / (survey.getPeakSunHours() * survey.getSystemDerateFactor());

        return new SizingResultDto(
                round(dailyConsumptionKwh),
                round(continuousLoadKw),
                round(peakLoadKw),
                round(recommendedInverterKw),
                round(recommendedBatteryCapacityKwh),
                round(recommendedPanelArrayKwp)
        );
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
