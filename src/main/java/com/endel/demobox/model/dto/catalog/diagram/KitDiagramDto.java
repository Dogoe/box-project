package com.endel.demobox.model.dto.catalog.diagram;

import com.endel.demobox.model.dto.catalog.compatibility.TerminalNeedDto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything the kit's wiring diagram shows, already worked out and worded: the drawing's labels,
 * the terminal tags, and the reference tables for the installer. Built from the kit review.
 */
@Data
public class KitDiagramDto {

    private String kitName;
    private String subtitle;
    /** False when the kit can't be drawn yet; {@link #blockers} says why. */
    private boolean drawable;
    private List<String> blockers = new ArrayList<>();

    private boolean hasPv;
    /** Separate charge controller (panels → controller → battery). */
    private boolean hasController;
    /** The inverter's own MPPT takes the panels. */
    private boolean builtInCharger;

    /** Circled reference number of each element in the drawing, e.g. "PANEL" → "①". */
    private Map<String, String> nums = new HashMap<>();
    /** Terminal tag at each lug position, e.g. "BAT_POS" → "T1". Missing positions aren't drawn. */
    private Map<String, String> tags = new HashMap<>();

    // Drawing labels
    private String panelTitle;
    private String panelLine2;
    private int panelCount;
    private String connectorLabel;
    private List<String> panelNotes = new ArrayList<>();
    private String controllerTitle;
    private String controllerScreen;
    private String batteryTitle;
    private int batteryCount;
    private List<String> batteryLines = new ArrayList<>();
    private String inverterTitle;
    private List<String> inverterLines = new ArrayList<>();
    private List<String> batteryRunLabels = new ArrayList<>();
    private String batteryRunGauge;
    private List<String> controllerRunLabels = new ArrayList<>();
    private List<String> acLabels = new ArrayList<>();
    private String chassisGroundLabel;

    private Slot pvDisconnect;
    private Slot controllerProtection;
    private Slot mainFuse;
    private Slot batterySwitch;
    private Slot acBreaker;

    // Tables
    private List<ComponentRow> components = new ArrayList<>();
    private List<CableRow> cables = new ArrayList<>();
    private List<ProtectionRow> protections = new ArrayList<>();
    private List<TagRow> tagRows = new ArrayList<>();
    private List<TerminalNeedDto> terminalTotals = new ArrayList<>();
    private List<String> connectSteps = new ArrayList<>();
    private List<String> disconnectSteps = new ArrayList<>();
    private List<String> rules = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();

    /** One protection drawn in the diagram. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Slot {
        /** "kit", "suggested" or "missing". */
        private String state;
        /** e.g. "Fusible principal". */
        private String title;
        /** Second label line: the product's spec, "sugerido", or what's missing. */
        private String line;
        /** Short text on the symbol, e.g. "80A", or "?" when missing. */
        private String body;
        private int quantity;

        public boolean isMissing() {
            return "missing".equals(state);
        }

        public boolean isSuggested() {
            return "suggested".equals(state);
        }
    }

    public record ComponentRow(String num, String element, String product, String quantity, String data) {
    }

    public record CableRow(String run, String cable, String current, String design, String ampacity, String length, String note) {
    }

    public record ProtectionRow(String num, String what, String requirement, String inKit, String state) {
    }

    public record TagRow(String ids, String where, String gauge, String size, String note) {
    }
}
