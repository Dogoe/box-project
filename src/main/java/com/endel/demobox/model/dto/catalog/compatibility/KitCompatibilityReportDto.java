package com.endel.demobox.model.dto.catalog.compatibility;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class KitCompatibilityReportDto {
    private List<CompatibilityCheckDto> checks = new ArrayList<>();
    /** e.g. "4 en serie x 1 en paralelo - 51.2 V, 100 Ah (5.1 kWh)"; null when not applicable. */
    private String batteryBank;
    /** e.g. "2 cadenas de 3 paneles en serie - ~110 V"; null when not applicable. */
    private String panelArray;
    /** Which product charges the batteries from the panels, e.g. "Integrado en el inversor · 60 A de carga". */
    private String solarCharger;
    private List<WiringSegmentDto> wiring = new ArrayList<>();
    /** Null when the kit has errors or lacks the data to work it out. */
    private KitExpansionDto expansion;

    public CompatibilitySeverity getStatus() {
        return checks.stream()
                .map(CompatibilityCheckDto::getSeverity)
                .max(Enum::compareTo)
                .orElse(CompatibilitySeverity.OK);
    }

    public void ok(String message) {
        checks.add(new CompatibilityCheckDto(CompatibilitySeverity.OK, message));
    }

    public void warn(String message) {
        checks.add(new CompatibilityCheckDto(CompatibilitySeverity.WARNING, message));
    }

    public void error(String message) {
        checks.add(new CompatibilityCheckDto(CompatibilitySeverity.ERROR, message));
    }
}
