package com.endel.demobox.model.dto.catalog.compatibility;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
    /** Fuses, breakers and disconnects the kit needs, and whether it includes them. */
    private List<ProtectionCheckDto> protections = new ArrayList<>();
    /** Cable lugs the kit needs, by gauge and stud size. Informational: never a warning. */
    private List<TerminalNeedDto> terminals = new ArrayList<>();
    /** Cables, protections and terminals the kit is missing, ready to add (merged by product). */
    private List<SuggestedItemDto> suggestedItems = new ArrayList<>();
    /** How the kit is wired, for the wiring diagram; null when the kit has no inverter or can't be worked out. */
    @JsonIgnore
    private KitLayoutDto layout;

    /** Adds a suggestion, summing quantities when the same product is already suggested. */
    public void suggest(SuggestedItemDto item) {
        for (SuggestedItemDto existing : suggestedItems) {
            if (existing.getProductId().equals(item.getProductId())) {
                existing.setQuantity(existing.getQuantity() + item.getQuantity());
                existing.setReason(existing.getReason() + "; " + item.getReason());
                return;
            }
        }
        suggestedItems.add(item);
    }

    public CompatibilitySeverity getStatus() {
        return checks.stream()
                .map(CompatibilityCheckDto::getSeverity)
                .max(Enum::compareTo)
                .orElse(CompatibilitySeverity.OK);
    }

    /** The kit part the next ok/warn/error messages are about (until changed again). */
    @JsonIgnore
    private KitSection currentSection = KitSection.GENERAL;

    public KitCompatibilityReportDto about(KitSection section) {
        this.currentSection = section;
        return this;
    }

    public void ok(String message) {
        add(CompatibilitySeverity.OK, message);
    }

    public void warn(String message) {
        add(CompatibilitySeverity.WARNING, message);
    }

    public void error(String message) {
        add(CompatibilitySeverity.ERROR, message);
    }

    private void add(CompatibilitySeverity severity, String message) {
        CompatibilityCheckDto check = new CompatibilityCheckDto(severity, message);
        check.setSection(currentSection);
        checks.add(check);
    }
}
