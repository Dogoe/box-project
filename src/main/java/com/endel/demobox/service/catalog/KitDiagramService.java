package com.endel.demobox.service.catalog;

import com.endel.demobox.exception.ResourceNotFoundException;
import com.endel.demobox.model.catalog.Battery;
import com.endel.demobox.model.catalog.BatteryChemistry;
import com.endel.demobox.model.catalog.ChargeController;
import com.endel.demobox.model.catalog.FuseClass;
import com.endel.demobox.model.catalog.Inverter;
import com.endel.demobox.model.catalog.Kit;
import com.endel.demobox.model.catalog.Protection;
import com.endel.demobox.model.catalog.ProtectionType;
import com.endel.demobox.model.catalog.SolarPanel;
import com.endel.demobox.model.catalog.WireGauge;
import com.endel.demobox.model.dto.catalog.compatibility.CompatibilityCheckDto;
import com.endel.demobox.model.dto.catalog.compatibility.CompatibilitySeverity;
import com.endel.demobox.model.dto.catalog.compatibility.KitCompatibilityReportDto;
import com.endel.demobox.model.dto.catalog.compatibility.KitLayoutDto;
import com.endel.demobox.model.dto.catalog.compatibility.ProtectionCheckDto;
import com.endel.demobox.model.dto.catalog.compatibility.ProtectionSlot;
import com.endel.demobox.model.dto.catalog.compatibility.WiringSegmentDto;
import com.endel.demobox.model.dto.catalog.diagram.KitDiagramDto;
import com.endel.demobox.model.dto.catalog.diagram.KitDiagramDto.Slot;
import com.endel.demobox.repository.catalog.KitRepository;
import com.endel.demobox.service.WiringSettingsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Builds a kit's wiring diagram for the installer from the kit review: which elements to draw
 * (separate controller or the inverter's own MPPT, the battery's built-in breaker, missing
 * protections), the values on each cable, the terminal (lug) at each connection, and the
 * reference tables and connection order. It only reads the review; it adds no rules of its own
 * except the AC and ground wire sizes, which the review doesn't cover.
 */
@Service
public class KitDiagramService {

    /** Reference order of the drawing's elements; each present one gets the next circled number. */
    private static final List<String> ELEMENT_ORDER = List.of("PANEL", "CONNECTOR", "PV_DISCONNECT", "CONTROLLER",
            "CONTROLLER_PROTECTION", "BATTERY", "MAIN_FUSE", "BATTERY_SWITCH", "INVERTER", "AC_BREAKER", "OUTLET", "GROUND");
    /** Bare copper from the panel frames and structure to the ground rod. */
    private static final WireGauge PANEL_GROUND = WireGauge.AWG_6;
    /** Thinnest inverter chassis ground most inverter manuals accept. */
    private static final WireGauge MIN_CHASSIS_GROUND = WireGauge.AWG_8;

    private final KitRepository kitRepository;
    private final KitCompatibilityService compatibilityService;
    private final WiringSettingsService wiringSettingsService;

    public KitDiagramService(KitRepository kitRepository, KitCompatibilityService compatibilityService,
                             WiringSettingsService wiringSettingsService) {
        this.kitRepository = kitRepository;
        this.compatibilityService = compatibilityService;
        this.wiringSettingsService = wiringSettingsService;
    }

    @Transactional(readOnly = true)
    public KitDiagramDto build(Long kitId) {
        Kit kit = kitRepository.findById(kitId).orElseThrow(() -> new ResourceNotFoundException("Kit not found: " + kitId));
        List<KitLine> lines = kit.getItems().stream().map(item -> new KitLine(item.getProduct(), item.getQuantity())).toList();
        KitCompatibilityReportDto report = compatibilityService.analyze(lines, wiringSettingsService.getCurrent());
        return build(kit.getName(), report);
    }

    KitDiagramDto build(String kitName, KitCompatibilityReportDto report) {
        KitDiagramDto d = new KitDiagramDto();
        d.setKitName(kitName);

        report.getChecks().stream().filter(c -> c.getSeverity() == CompatibilitySeverity.ERROR)
                .map(CompatibilityCheckDto::getMessage).forEach(d.getBlockers()::add);
        KitLayoutDto layout = report.getLayout();
        if (d.getBlockers().isEmpty()) {
            if (layout == null || layout.getInverter() == null) {
                d.getBlockers().add("El kit no tiene inversor.");
            } else if (layout.getBattery() == null || layout.getBatteryRun() == null || layout.getBatteryRun().getGauge() == null) {
                d.getBlockers().add("El diagrama es para kits con baterías: agrega la batería (y captura su voltaje y Ah) para poder dibujarlo.");
            }
        }
        if (!d.getBlockers().isEmpty()) {
            return d;
        }
        d.setDrawable(true);

        Map<ProtectionSlot, ProtectionCheckDto> rows = new EnumMap<>(ProtectionSlot.class);
        for (ProtectionCheckDto row : report.getProtections()) {
            rows.putIfAbsent(row.getSlot(), row);
        }

        Inverter inverter = layout.getInverter();
        Battery battery = layout.getBattery();
        ChargeController controller = layout.getController();
        SolarPanel panel = layout.getPanel();
        WiringSegmentDto batteryRun = layout.getBatteryRun();
        WiringSegmentDto controllerRun = layout.getController() != null ? layout.getControllerRun() : null;
        WiringSegmentDto pvRun = panel != null ? layout.getPvRun() : null;

        d.setHasPv(pvRun != null);
        d.setBuiltInCharger(layout.isBuiltInCharger() && pvRun != null);
        d.setHasController(controller != null && controllerRun != null && controllerRun.getGauge() != null);

        ProtectionCheckDto mainFuseRow = rows.get(ProtectionSlot.MAIN_FUSE);
        ProtectionCheckDto switchRow = rows.get(ProtectionSlot.BATTERY_SWITCH);
        boolean drawMainFuse = mainFuseRow != null && !mainFuseRow.isBuiltIntoBattery();
        boolean drawSwitch = switchRow != null && !switchRow.isBuiltIntoBattery();
        boolean drawAc = rows.containsKey(ProtectionSlot.AC_BREAKER);

        // Element numbers
        List<String> present = new ArrayList<>();
        if (d.isHasPv()) {
            present.addAll(List.of("PANEL", "CONNECTOR", "PV_DISCONNECT"));
        }
        if (d.isHasController()) {
            present.addAll(List.of("CONTROLLER", "CONTROLLER_PROTECTION"));
        }
        present.add("BATTERY");
        if (drawMainFuse) {
            present.add("MAIN_FUSE");
        }
        if (drawSwitch) {
            present.add("BATTERY_SWITCH");
        }
        present.add("INVERTER");
        if (drawAc) {
            present.add("AC_BREAKER");
        }
        present.addAll(List.of("OUTLET", "GROUND"));
        int n = 0;
        for (String element : ELEMENT_ORDER) {
            if (present.contains(element)) {
                d.getNums().put(element, circled(++n));
            }
        }
        Map<String, String> num = d.getNums();

        double busVolts = layout.getBusVolts();
        double chargeVolts = round1(busVolts * KitProtectionPlanner.CHARGE_VOLTAGE_FACTOR);
        double acAmps = inverter.getRatedPowerKw() * 1000 / KitProtectionPlanner.AC_VOLTS;
        String batteryGauge = gaugeLabel(batteryRun);

        // Subtitle
        List<String> parts = new ArrayList<>();
        parts.add(fmt(busVolts) + " V");
        parts.add("inversor " + power(inverter.getRatedPowerKw()));
        if (d.isHasPv()) {
            parts.add(layout.getPanelCount() + (layout.getPanelCount() == 1 ? " panel de " : " paneles de ") + fmt(panel.getWattage()) + " W");
        }
        if (d.isHasController()) {
            parts.add("controlador " + fmt(controller.getMaxChargeCurrentA()) + " A");
        }
        parts.add(layout.getBatteryCount() + (layout.getBatteryCount() == 1 ? " batería " : " baterías ") + fmt(battery.getVoltage()) + " V "
                + fmt(battery.getCapacityAh()) + " Ah");
        d.setSubtitle(String.join(" · ", parts));

        // Panels
        if (d.isHasPv()) {
            d.setPanelCount(layout.getPanelCount());
            d.setConnectorLabel(panel.getConnector().getLabel());
            d.setPanelTitle(num.get("PANEL") + " Paneles: " + layout.getPanelCount() + " × " + fmt(panel.getWattage()) + " W");
            d.setPanelLine2(layout.getPvSeries() + " en serie × " + layout.getPvStrings() + (layout.getPvStrings() == 1 ? " cadena" : " cadenas"));
            d.getPanelNotes().add("Cada panel: Voc " + fmt(panel.getVoc()) + " V · Isc " + fmt(panel.getIsc()) + " A");
            d.getPanelNotes().add("Arreglo: hasta " + fmt(layout.getPvColdStringVoc()) + " V (en frío) · " + fmt(pvRun.getOperatingCurrentA()) + " A");
            d.getPanelNotes().add("Cable: " + gaugeLabel(pvRun) + " solar · rojo (+) / negro (−)");
            ProtectionCheckDto stringFuses = rows.get(ProtectionSlot.PV_STRING_FUSE);
            if (stringFuses != null) {
                d.getPanelNotes().add("Fusible por cadena: " + stringFuses.getQuantity() + " × " + fmt(pvRun.getFuseA()) + " A gPV"
                        + (stringFuses.isCovered() ? "" : " (FALTA)"));
            }
            d.setPvDisconnect(slot(rows.get(ProtectionSlot.PV_DISCONNECT), "Desconectador PV"));
        }

        // Controller
        if (d.isHasController()) {
            d.setControllerTitle(num.get("CONTROLLER") + " Controlador de carga " + fmt(controller.getMaxChargeCurrentA()) + " A · " + fmt(busVolts) + " V");
            d.setControllerScreen(fmt(chargeVolts) + "V " + fmt(controller.getMaxChargeCurrentA()) + "A");
            d.setControllerProtection(slot(rows.get(ProtectionSlot.CONTROLLER), "Protección controlador"));
            d.getControllerRunLabels().add(gaugeLabel(controllerRun) + " rojo/negro");
            d.getControllerRunLabels().add(fmt(controllerRun.getOperatingCurrentA()) + " A · máx " + fmt(ampacity(controllerRun)) + " A");
        }

        // Battery
        d.setBatteryCount(layout.getBatteryCount());
        d.setBatteryTitle(num.get("BATTERY") + (layout.getBatteryCount() == 1 ? " Batería " : " Baterías ") + fmt(busVolts) + " V");
        d.getBatteryLines().add((layout.getBatteryCount() > 1 ? layout.getBatteryCount() + " × " : "") + chemistry(battery.getChemistry()) + " "
                + fmt(battery.getVoltage()) + " V · " + fmt(battery.getCapacityAh()) + " Ah");
        if (layout.getBatteryCount() > 1) {
            d.getBatteryLines().add(layout.getBatterySeries() + " en serie × " + layout.getBatteryParallel() + " en paralelo");
        }
        d.getBatteryLines().add("carga a ~" + fmt(chargeVolts) + " V · bornes " + battery.getTerminalSize().getLabel());
        if (battery.getIntegratedBreakerA() != null) {
            d.getBatteryLines().add("breaker integrado " + fmt(battery.getIntegratedBreakerA()) + " A"
                    + (drawSwitch ? "" : ": hace de switch") + (drawMainFuse || drawSwitch ? "" : " y fusible"));
        }

        // Main fuse and switch
        if (drawMainFuse) {
            d.setMainFuse(slot(mainFuseRow, "Fusible principal"));
        }
        if (drawSwitch) {
            d.setBatterySwitch(slot(switchRow, "Switch de batería"));
        }
        d.setBatteryRunGauge(batteryGauge);
        d.getBatteryRunLabels().add("Batería ↔ inversor: " + batteryGauge + " rojo (+) / negro (−)");
        d.getBatteryRunLabels().add(fmt(batteryRun.getOperatingCurrentA()) + " A normal · diseño " + fmt(batteryRun.getDesignCurrentA())
                + " A · el cable aguanta " + fmt(ampacity(batteryRun)) + " A");
        d.getBatteryRunLabels().add(fmt(batteryRun.getLengthMeters()) + " m por lado · caída " + fmt(batteryRun.getVoltageDropPercent()) + " %"
                + " · el negativo va directo, sin fusible");

        // Inverter
        d.setInverterTitle(num.get("INVERTER") + " Inversor " + power(inverter.getRatedPowerKw()));
        d.getInverterLines().add(fmt(busVolts) + " V DC → 120 V AC");
        d.getInverterLines().add("Entrada DC: " + fmt(batteryRun.getOperatingCurrentA()) + " A");
        d.getInverterLines().add("Salida AC: " + fmt(acAmps) + " A");
        if (d.isBuiltInCharger()) {
            d.getInverterLines().add("MPPT integrado" + (inverter.getMpptMaxChargeCurrentA() != null
                    ? ": carga " + fmt(inverter.getMpptMaxChargeCurrentA()) + " A" : ""));
        }
        d.getInverterLines().add("bornes DC " + inverter.getTerminalSize().getLabel());

        // AC side
        ProtectionCheckDto acRow = rows.get(ProtectionSlot.AC_BREAKER);
        double acBreakerA = acBreakerRating(acRow, acAmps);
        WireGauge acGauge = smallestGaugeFor(acBreakerA);
        if (drawAc) {
            d.setAcBreaker(slot(acRow, "Breaker AC"));
        }
        d.getAcLabels().add("AC: " + (acGauge == null ? "según el breaker" : acGauge.getLabel()) + " THHN");
        d.getAcLabels().add("L negro · N blanco · G verde");
        d.getAcLabels().add(fmt(acAmps) + " A (" + power(inverter.getRatedPowerKw()) + " ÷ 120 V)");

        // Ground
        WireGauge chassis = chassisGround(mainFuseRow, batteryRun);
        d.setChassisGroundLabel(chassis.getLabel() + " verde");

        tags(d, battery, inverter, mainFuseRow, drawMainFuse, switchRow, drawSwitch, rows.get(ProtectionSlot.CONTROLLER), batteryGauge,
                d.isHasController() ? gaugeLabel(controllerRun) : null, chassis, d.isHasPv());
        d.setTerminalTotals(report.getTerminals());

        // Tables
        components(d, layout, report);
        cables(d, layout, batteryRun, controllerRun, pvRun, acAmps, acBreakerA, acGauge, chassis);
        protections(d, report);
        steps(d, drawMainFuse, drawSwitch, rows.get(ProtectionSlot.CONTROLLER), battery);
        rules(d, mainFuseRow, drawMainFuse, battery, busVolts, layout);
        report.getChecks().stream().filter(c -> c.getSeverity() == CompatibilitySeverity.WARNING)
                .map(CompatibilityCheckDto::getMessage).forEach(d.getWarnings()::add);
        return d;
    }

    // ---- Protections ----

    private static Slot slot(ProtectionCheckDto row, String title) {
        if (row == null) {
            return null;
        }
        Protection p = row.getProduct();
        String state = row.isCovered() ? "kit" : p != null ? "suggested" : "missing";
        String line = p == null ? "FALTA · ver tabla de protecciones"
                : spec(p) + (row.getQuantity() > 1 ? " · × " + row.getQuantity() : "") + ("suggested".equals(state) ? " (sugerido)" : "");
        String body = p == null ? "?" : fmt(p.getRatedCurrentA()) + "A";
        return new Slot(state, title, line, body, row.getQuantity());
    }

    /** Short spec for a drawing label, e.g. "Fusible Clase T 150 A" or "Breaker DC 250 A · 24 V". */
    static String spec(Protection p) {
        return switch (p.getProtectionType()) {
            case FUSE -> "Fusible " + (p.getFuseClass() == null || p.getFuseClass() == FuseClass.OTHER ? "" : p.getFuseClass().getLabel() + " ")
                    + fmt(p.getRatedCurrentA()) + " A · " + fmt(p.getMaxVoltage()) + " V";
            case DC_BREAKER -> "Breaker DC " + fmt(p.getRatedCurrentA()) + " A · " + fmt(p.getMaxVoltage()) + " V";
            case PV_DISCONNECT -> fmt(p.getRatedCurrentA()) + " A · " + fmt(p.getMaxVoltage()) + " V DC · " + p.getPolesOrOne() + " polos";
            case AC_BREAKER -> "Breaker AC " + fmt(p.getRatedCurrentA()) + " A · " + p.getPolesOrOne() + (p.getPolesOrOne() == 1 ? " polo" : " polos");
        };
    }

    private static boolean isMrbf(Protection p) {
        return p != null && p.getProtectionType() == ProtectionType.FUSE && p.getFuseClass() == FuseClass.MRBF;
    }

    // ---- Terminals ----

    private static final class TagCounter {
        int next = 1;

        String take() {
            return "T" + next++;
        }
    }

    /**
     * One tag per lug position, matching what the kit review counts: both ends of the battery
     * cables, two per protection in series (except an MRBF, which bolts onto the battery post),
     * and the controller cables' battery end plus its protection. Ground lugs come last.
     */
    private static void tags(KitDiagramDto d, Battery battery, Inverter inverter, ProtectionCheckDto mainFuseRow, boolean drawMainFuse,
                             ProtectionCheckDto switchRow, boolean drawSwitch, ProtectionCheckDto controllerRow,
                             String batteryGauge, String controllerGauge, WireGauge chassis, boolean hasPv) {
        TagCounter t = new TagCounter();
        Map<String, String> tags = d.getTags();
        String batteryStud = battery.getTerminalSize().getLabel();
        String inverterStud = inverter.getTerminalSize().getLabel();

        tags.put("BAT_POS", t.take());
        d.getTagRows().add(new KitDiagramDto.TagRow(tags.get("BAT_POS"), "Batería + (cable al inversor)", batteryGauge, batteryStud, null));
        if (drawMainFuse) {
            Protection fuse = mainFuseRow.getProduct();
            if (isMrbf(fuse)) {
                d.getTagRows().add(new KitDiagramDto.TagRow("—", "Fusible MRBF", batteryGauge, "—",
                        "Se atornilla en el borne + de la batería; la terminal del cable va encima (no lleva terminales propias)."));
            } else {
                tags.put("FUSE_IN", t.take());
                tags.put("FUSE_OUT", t.take());
                d.getTagRows().add(new KitDiagramDto.TagRow(tags.get("FUSE_IN") + " / " + tags.get("FUSE_OUT"), "Fusible principal (entrada / salida)",
                        batteryGauge, studOf(fuse), fuse == null ? "Cuando tengas el fusible, revisa su borne." : null));
            }
        }
        if (drawSwitch) {
            Protection breaker = switchRow.getProduct();
            tags.put("SW_IN", t.take());
            tags.put("SW_OUT", t.take());
            d.getTagRows().add(new KitDiagramDto.TagRow(tags.get("SW_IN") + " / " + tags.get("SW_OUT"), "Switch de batería (entrada / salida)",
                    batteryGauge, studOf(breaker), breaker == null ? "Cuando tengas el switch, revisa su borne." : "Mide el tornillo: 5/16\" = M8, 3/8\" = M10."));
        }
        tags.put("INV_POS", t.take());
        d.getTagRows().add(new KitDiagramDto.TagRow(tags.get("INV_POS"), "Inversor +", batteryGauge, inverterStud, null));
        tags.put("BAT_NEG", t.take());
        d.getTagRows().add(new KitDiagramDto.TagRow(tags.get("BAT_NEG"), "Batería −", batteryGauge, batteryStud, null));
        tags.put("INV_NEG", t.take());
        d.getTagRows().add(new KitDiagramDto.TagRow(tags.get("INV_NEG"), "Inversor −", batteryGauge, inverterStud, null));

        if (controllerGauge != null) {
            Protection protection = controllerRow == null ? null : controllerRow.getProduct();
            if (controllerRow != null && !isMrbf(protection)) {
                tags.put("CTL_PROT_IN", t.take());
                tags.put("CTL_PROT_OUT", t.take());
                d.getTagRows().add(new KitDiagramDto.TagRow(tags.get("CTL_PROT_IN") + " / " + tags.get("CTL_PROT_OUT"),
                        "Protección del controlador (entrada / salida)", controllerGauge, studOf(protection),
                        protection == null ? "Cuando la tengas, revisa su borne." : "Si el portafusible trae cables, van con empalmes en vez de terminales."));
            }
            tags.put("CTL_BAT_POS", t.take());
            tags.put("CTL_BAT_NEG", t.take());
            d.getTagRows().add(new KitDiagramDto.TagRow(tags.get("CTL_BAT_POS") + " / " + tags.get("CTL_BAT_NEG"),
                    "Controlador → batería + / − (van en el mismo borne que " + tags.get("BAT_POS") + " / " + tags.get("BAT_NEG") + ")",
                    controllerGauge, batteryStud, "Del lado del controlador van punteras (tornillo), no terminales."));
        }

        tags.put("GND_INV", t.take());
        d.getTagRows().add(new KitDiagramDto.TagRow(tags.get("GND_INV"), "Chasis del inversor (tierra)", chassis.getLabel(), "el de la carcasa",
                "No la cuenta la revisión."));
        if (hasPv) {
            tags.put("GND_PANEL", t.take());
            d.getTagRows().add(new KitDiagramDto.TagRow(tags.get("GND_PANEL"), "Marco de cada panel (tierra)", PANEL_GROUND.getLabel() + " desnudo",
                    "zapata de tierra", "Una por panel, en el agujero de tierra del marco. No la cuenta la revisión."));
        }
    }

    private static String studOf(Protection p) {
        return p == null ? "según la pieza" : p.getTerminalSize().getLabel();
    }

    // ---- Tables ----

    private static void components(KitDiagramDto d, KitLayoutDto layout, KitCompatibilityReportDto report) {
        Map<String, String> num = d.getNums();
        List<KitDiagramDto.ComponentRow> rows = d.getComponents();
        if (d.isHasPv()) {
            SolarPanel panel = layout.getPanel();
            rows.add(new KitDiagramDto.ComponentRow(num.get("PANEL"), "Paneles", panel.getName(), String.valueOf(layout.getPanelCount()),
                    fmt(panel.getWattage()) + " W · Voc " + fmt(panel.getVoc()) + " V · Isc " + fmt(panel.getIsc()) + " A · "
                            + report.getPanelArray()));
            rows.add(new KitDiagramDto.ComponentRow(num.get("CONNECTOR"), "Conectores", panel.getConnector().getLabel(), "—",
                    "Los que trae el cable del panel"));
            rows.add(protectionComponent(num.get("PV_DISCONNECT"), "Desconectador PV", report, ProtectionSlot.PV_DISCONNECT));
        }
        if (d.isHasController()) {
            ChargeController controller = layout.getController();
            rows.add(new KitDiagramDto.ComponentRow(num.get("CONTROLLER"), "Controlador de carga", controller.getName(), "1",
                    fmt(controller.getMaxChargeCurrentA()) + " A de carga · PV máx " + fmt(controller.getMaxInputVoltage()) + " V"));
            rows.add(protectionComponent(num.get("CONTROLLER_PROTECTION"), "Protección del controlador", report, ProtectionSlot.CONTROLLER));
        }
        Battery battery = layout.getBattery();
        rows.add(new KitDiagramDto.ComponentRow(num.get("BATTERY"), "Batería", battery.getName(), String.valueOf(layout.getBatteryCount()),
                report.getBatteryBank()));
        if (num.containsKey("MAIN_FUSE")) {
            rows.add(protectionComponent(num.get("MAIN_FUSE"), "Fusible principal", report, ProtectionSlot.MAIN_FUSE));
        }
        if (num.containsKey("BATTERY_SWITCH")) {
            rows.add(protectionComponent(num.get("BATTERY_SWITCH"), "Switch de batería", report, ProtectionSlot.BATTERY_SWITCH));
        }
        Inverter inverter = layout.getInverter();
        rows.add(new KitDiagramDto.ComponentRow(num.get("INVERTER"), "Inversor", inverter.getName(), "1",
                power(inverter.getRatedPowerKw()) + " · " + fmt(layout.getBusVolts()) + " V"
                        + (layout.isBuiltInCharger() && report.getSolarCharger() != null ? " · MPPT " + report.getSolarCharger().toLowerCase(Locale.ROOT) : "")));
        if (num.containsKey("AC_BREAKER")) {
            rows.add(protectionComponent(num.get("AC_BREAKER"), "Breaker AC", report, ProtectionSlot.AC_BREAKER));
        }
        rows.add(new KitDiagramDto.ComponentRow(num.get("OUTLET"), "Contactos / cargas", "—", "—", "120 V AC"));
        rows.add(new KitDiagramDto.ComponentRow(num.get("GROUND"), "Tierra", "Varilla copperweld 5/8\" × 2.4 m + abrazadera + barra", "1",
                "Cable " + PANEL_GROUND.getLabel() + " cobre desnudo a la varilla"));
    }

    private static KitDiagramDto.ComponentRow protectionComponent(String num, String element, KitCompatibilityReportDto report, ProtectionSlot slot) {
        ProtectionCheckDto row = report.getProtections().stream().filter(r -> r.getSlot() == slot).findFirst().orElse(null);
        if (row == null) {
            return new KitDiagramDto.ComponentRow(num, element, "—", "—", "");
        }
        Protection p = row.getProduct();
        String product = p == null ? "FALTA" : p.getName() + (row.isCovered() ? "" : " (sugerido, no está en el kit)");
        return new KitDiagramDto.ComponentRow(num, element, product, String.valueOf(row.getQuantity()),
                p == null ? "Pide: " + row.getRequirement() : p.getSpecLabel());
    }

    private static void cables(KitDiagramDto d, KitLayoutDto layout, WiringSegmentDto batteryRun, WiringSegmentDto controllerRun,
                               WiringSegmentDto pvRun, double acAmps, double acBreakerA, WireGauge acGauge, WireGauge chassis) {
        Map<String, String> num = d.getNums();
        List<KitDiagramDto.CableRow> rows = d.getCables();
        if (pvRun != null) {
            String to = d.isHasController() ? num.get("CONTROLLER") + " controlador" : num.get("INVERTER") + " inversor";
            rows.add(new KitDiagramDto.CableRow(num.get("PANEL") + " paneles → " + num.get("PV_DISCONNECT") + " desconectador → " + to,
                    gaugeLabel(pvRun) + " cable solar, " + pvRun.getConnector(), fmt(pvRun.getOperatingCurrentA()) + " A",
                    fmt(pvRun.getDesignCurrentA()) + " A", fmt(ampacity(pvRun)) + " A", fmt(pvRun.getLengthMeters()) + " m",
                    "Hasta " + fmt(layout.getPvColdStringVoc()) + " V en frío · caída " + fmt(pvRun.getVoltageDropPercent()) + " %"));
        }
        if (d.isHasController()) {
            rows.add(new KitDiagramDto.CableRow(num.get("CONTROLLER") + " controlador → " + num.get("CONTROLLER_PROTECTION") + " protección → "
                    + num.get("BATTERY") + " batería", gaugeLabel(controllerRun) + " rojo/negro", fmt(controllerRun.getOperatingCurrentA()) + " A",
                    fmt(controllerRun.getDesignCurrentA()) + " A", fmt(ampacity(controllerRun)) + " A", fmt(controllerRun.getLengthMeters()) + " m",
                    "Caída " + fmt(controllerRun.getVoltageDropPercent()) + " %"));
        }
        StringBuilder path = new StringBuilder(num.get("BATTERY") + " batería");
        if (num.containsKey("MAIN_FUSE")) {
            path.append(" → ").append(num.get("MAIN_FUSE")).append(" fusible");
        }
        if (num.containsKey("BATTERY_SWITCH")) {
            path.append(" → ").append(num.get("BATTERY_SWITCH")).append(" switch");
        }
        path.append(" → ").append(num.get("INVERTER")).append(" inversor");
        rows.add(new KitDiagramDto.CableRow(path.toString(), gaugeLabel(batteryRun) + " rojo/negro", fmt(batteryRun.getOperatingCurrentA()) + " A",
                fmt(batteryRun.getDesignCurrentA()) + " A", fmt(ampacity(batteryRun)) + " A", fmt(batteryRun.getLengthMeters()) + " m",
                "Caída " + fmt(batteryRun.getVoltageDropPercent()) + " %" + (batteryRun.getCurrentNote() != null ? " · " + batteryRun.getCurrentNote() : "")));
        rows.add(new KitDiagramDto.CableRow(num.get("INVERTER") + " inversor → " + (num.containsKey("AC_BREAKER") ? num.get("AC_BREAKER") + " breaker → " : "")
                + num.get("OUTLET") + " contactos", (acGauge == null ? "según el breaker" : acGauge.getLabel()) + " THHN: L negro, N blanco, G verde",
                fmt(acAmps) + " A", fmt(acBreakerA) + " A (breaker)", acGauge == null ? "—" : fmt(acGauge.getAmpacityA()) + " A", "—",
                "Calibre por el breaker: el cable debe aguantar lo que deja pasar"));
        rows.add(new KitDiagramDto.CableRow("Chasis del inversor → barra de tierra", chassis.getLabel() + " verde", "—", "—", "—", "—",
                "O lo que pida el manual del inversor si es más grueso"));
        if (pvRun != null) {
            rows.add(new KitDiagramDto.CableRow("Marcos de paneles y estructura → barra → varilla", PANEL_GROUND.getLabel() + " cobre desnudo",
                    "—", "—", "—", "—", "Una zapata de tierra por panel"));
        } else {
            rows.add(new KitDiagramDto.CableRow("Barra → varilla", PANEL_GROUND.getLabel() + " cobre desnudo", "—", "—", "—", "—", ""));
        }
    }

    private static void protections(KitDiagramDto d, KitCompatibilityReportDto report) {
        Map<ProtectionSlot, String> element = Map.of(ProtectionSlot.MAIN_FUSE, "MAIN_FUSE", ProtectionSlot.BATTERY_SWITCH, "BATTERY_SWITCH",
                ProtectionSlot.CONTROLLER, "CONTROLLER_PROTECTION", ProtectionSlot.PV_DISCONNECT, "PV_DISCONNECT",
                ProtectionSlot.PV_STRING_FUSE, "PV_DISCONNECT", ProtectionSlot.AC_BREAKER, "AC_BREAKER");
        for (ProtectionCheckDto row : report.getProtections()) {
            String num = row.getSlot() == null ? "" : d.getNums().getOrDefault(row.isBuiltIntoBattery() ? "BATTERY" : element.get(row.getSlot()), "");
            String inKit;
            String state;
            if (row.isBuiltIntoBattery()) {
                inKit = row.getCoveredBy();
                state = "kit";
            } else if (row.isCovered()) {
                inKit = row.getCoveredBy();
                state = "kit";
            } else if (row.getProduct() != null) {
                inKit = "Sugerido: " + row.getProduct().getName() + (row.getProblem() != null ? " · " + row.getProblem() : "");
                state = "suggested";
            } else {
                inKit = "FALTA" + (row.getProblem() != null ? " · " + row.getProblem() : " · dala de alta en Protecciones");
                state = "missing";
            }
            String quantity = row.getQuantity() > 1 ? row.getQuantity() + " × " : "";
            d.getProtections().add(new KitDiagramDto.ProtectionRow(num, quantity + row.getLabel(), row.getRequirement(), inKit, state));
        }
    }

    private static void steps(KitDiagramDto d, boolean drawMainFuse, boolean drawSwitch, ProtectionCheckDto controllerRow, Battery battery) {
        Map<String, String> num = d.getNums();
        List<String> on = d.getConnectSteps();
        on.add("Tierra: clava la varilla " + num.get("GROUND") + ", pon la barra y conecta el chasis del inversor"
                + (d.isHasPv() ? " y los marcos de los paneles." : "."));
        StringBuilder dc = new StringBuilder("Batería " + num.get("BATTERY") + " +");
        if (drawMainFuse) {
            dc.append(" → fusible principal ").append(num.get("MAIN_FUSE"));
        }
        if (drawSwitch) {
            dc.append(" → switch ").append(num.get("BATTERY_SWITCH")).append(" (apagado)");
        } else if (battery.getIntegratedBreakerA() != null) {
            dc.append(" (con su breaker integrado apagado)");
        }
        dc.append(" → inversor ").append(num.get("INVERTER")).append(" (apagado). El negativo va directo de batería a inversor.");
        on.add(dc.toString());
        if (d.isHasController()) {
            boolean breaker = controllerRow != null && controllerRow.getProduct() != null
                    && controllerRow.getProduct().getProtectionType() == ProtectionType.DC_BREAKER;
            on.add("Controlador " + num.get("CONTROLLER") + " → batería " + (breaker ? "con su breaker " + num.get("CONTROLLER_PROTECTION") + " abierto; luego ciérralo"
                    : "sin el fusible " + num.get("CONTROLLER_PROTECTION") + " puesto; luego ponlo") + ": el controlador debe prender.");
        }
        String switchOn = drawSwitch ? "Enciende el switch " + num.get("BATTERY_SWITCH") + " y luego el inversor " + num.get("INVERTER") + "."
                : "Enciende la batería y luego el inversor " + num.get("INVERTER") + ".";
        String pv = "Con el desconectador " + num.get("PV_DISCONNECT") + " abierto, conecta los paneles (" + d.getConnectorLabel() + ") y de ahí "
                + (d.isHasController() ? "al controlador " + num.get("CONTROLLER") : "a la entrada PV del inversor " + num.get("INVERTER"))
                + ". Mide polaridad y voltaje; luego cierra el desconectador.";
        if (d.isHasController()) {
            if (d.isHasPv()) {
                on.add(pv);
            }
            on.add(switchOn);
        } else {
            on.add(switchOn);
            if (d.isHasPv()) {
                on.add(pv);
            }
        }
        on.add(num.containsKey("AC_BREAKER") ? "Al final sube el breaker AC " + num.get("AC_BREAKER") + " y conecta las cargas." : "Conecta las cargas.");

        List<String> off = d.getDisconnectSteps();
        if (num.containsKey("AC_BREAKER")) {
            off.add("Baja el breaker AC " + num.get("AC_BREAKER"));
        }
        if (d.isHasController()) {
            off.add("apaga el inversor " + num.get("INVERTER"));
            if (d.isHasPv()) {
                off.add("abre el desconectador PV " + num.get("PV_DISCONNECT"));
            }
            off.add(drawSwitch ? "apaga el switch " + num.get("BATTERY_SWITCH") : "apaga la batería");
            off.add("quita la protección del controlador " + num.get("CONTROLLER_PROTECTION"));
        } else {
            if (d.isHasPv()) {
                off.add("abre el desconectador PV " + num.get("PV_DISCONNECT"));
            }
            off.add("apaga el inversor " + num.get("INVERTER"));
            off.add(drawSwitch ? "apaga el switch " + num.get("BATTERY_SWITCH") : "apaga la batería");
        }
    }

    private static void rules(KitDiagramDto d, ProtectionCheckDto mainFuseRow, boolean drawMainFuse, Battery battery, double busVolts,
                              KitLayoutDto layout) {
        List<String> rules = d.getRules();
        if (drawMainFuse) {
            if (isMrbf(mainFuseRow.getProduct())) {
                rules.add("El fusible MRBF se atornilla directo en el borne + de la batería.");
            } else {
                rules.add("El fusible principal va lo más cerca posible del + de la batería (máx. 18 cm de cable).");
            }
            if (mainFuseRow.getRequirement() != null && mainFuseRow.getRequirement().contains("Clase T")) {
                rules.add("Banco de litio de " + fmt(busVolts) + " V: el fusible principal debe ser Clase T (corta el corto de la batería sin riesgo).");
            }
        }
        rules.add("Nunca pongas fusible ni breaker en el negativo DC.");
        rules.add("Los breakers DC tienen polaridad: respeta + y − marcados.");
        rules.add("Mide voltaje y polaridad antes de cada conexión.");
        rules.add("Aprieta los bornes al torque del fabricante y tápalos con capuchón.");
        if (d.isHasController()) {
            rules.add("Al controlador: siempre batería antes que paneles; para quitarlo, paneles antes que batería.");
        }
        if (d.isHasPv()) {
            rules.add("No desconectes los " + d.getConnectorLabel() + " con los paneles produciendo: abre primero el desconectador PV.");
        }
        if (layout.getBatteryCount() > 1) {
            rules.add("Baterías: " + layout.getBatterySeries() + " en serie × " + layout.getBatteryParallel()
                    + " en paralelo; une los puentes como indique el fabricante.");
        }
        if (battery.getChemistry() != null && !battery.getChemistry().hasBms()) {
            rules.add("Batería de plomo: ventílala y no la pongas en un cuarto cerrado con el inversor.");
        }
        rules.add("Unión neutro–tierra: una sola, según el manual del inversor.");
    }

    // ---- Helpers ----

    private static double acBreakerRating(ProtectionCheckDto row, double acAmps) {
        if (row != null && row.getProduct() != null) {
            return row.getProduct().getRatedCurrentA();
        }
        Integer size = KitCompatibilityService.standardFuse(acAmps * KitProtectionPlanner.CONTINUOUS_FACTOR);
        return size == null ? acAmps * KitProtectionPlanner.CONTINUOUS_FACTOR : size;
    }

    /** Thinnest wire whose ampacity covers the breaker in front of it. */
    static WireGauge smallestGaugeFor(double breakerA) {
        for (WireGauge gauge : WireGauge.values()) {
            if (gauge.getAmpacityA() + 1e-9 >= breakerA) {
                return gauge;
            }
        }
        return null;
    }

    /**
     * Equipment ground for the inverter chassis, by the main fuse in front of it (NEC 250.122:
     * up to 60 A → 10 AWG, 100 A → 8, 200 A → 6, 300 A → 4, more → 2), never thinner than 8 AWG.
     */
    static WireGauge chassisGround(ProtectionCheckDto mainFuseRow, WiringSegmentDto batteryRun) {
        double fuseA = mainFuseRow != null && mainFuseRow.getProduct() != null ? mainFuseRow.getProduct().getRatedCurrentA()
                : batteryRun.getFuseA() != null ? batteryRun.getFuseA() : batteryRun.getDesignCurrentA();
        WireGauge byFuse = fuseA <= 60 ? WireGauge.AWG_10 : fuseA <= 100 ? WireGauge.AWG_8 : fuseA <= 200 ? WireGauge.AWG_6
                : fuseA <= 300 ? WireGauge.AWG_4 : WireGauge.AWG_2;
        return byFuse.compareTo(MIN_CHASSIS_GROUND) >= 0 ? byFuse : MIN_CHASSIS_GROUND;
    }

    private static String gaugeLabel(WiringSegmentDto run) {
        WireGauge gauge = run.getCableGauge() != null ? run.getCableGauge() : run.getGauge();
        return gauge == null ? "—" : gauge.getLabel();
    }

    private static double ampacity(WiringSegmentDto run) {
        WireGauge gauge = run.getCableGauge() != null ? run.getCableGauge() : run.getGauge();
        return gauge == null ? 0 : gauge.getAmpacityA();
    }

    private static String chemistry(BatteryChemistry chemistry) {
        if (chemistry == null) {
            return "";
        }
        return switch (chemistry) {
            case LIFEPO4 -> "LiFePO4";
            case LITHIUM_ION -> "Litio-ion";
            case LEAD_ACID -> "Plomo-ácido";
            case GEL -> "Gel";
            case AGM -> "AGM";
        };
    }

    private static String power(double kw) {
        return kw < 1 ? fmt(kw * 1000) + " W" : fmt(kw) + " kW";
    }

    static String circled(int n) {
        return n >= 1 && n <= 20 ? String.valueOf((char) ('①' + n - 1)) : "(" + n + ")";
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    static String fmt(Double value) {
        if (value == null) {
            return "—";
        }
        double rounded = round1(value);
        return rounded == Math.rint(rounded) ? String.format(Locale.US, "%.0f", rounded) : String.format(Locale.US, "%.1f", rounded);
    }

    static String fmt(Integer value) {
        return value == null ? "—" : String.valueOf(value);
    }
}
