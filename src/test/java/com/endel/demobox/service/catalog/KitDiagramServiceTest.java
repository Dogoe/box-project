package com.endel.demobox.service.catalog;

import com.endel.demobox.model.catalog.Battery;
import com.endel.demobox.model.catalog.BatteryChemistry;
import com.endel.demobox.model.catalog.ChargeController;
import com.endel.demobox.model.catalog.FuseClass;
import com.endel.demobox.model.catalog.Inverter;
import com.endel.demobox.model.catalog.InverterType;
import com.endel.demobox.model.catalog.Protection;
import com.endel.demobox.model.catalog.ProtectionType;
import com.endel.demobox.model.catalog.SolarPanel;
import com.endel.demobox.model.catalog.TerminalSize;
import com.endel.demobox.model.dto.WiringSettingsDto;
import com.endel.demobox.model.dto.catalog.diagram.KitDiagramDto;
import com.endel.demobox.repository.catalog.ConnectionCableRepository;
import com.endel.demobox.repository.catalog.KitRepository;
import com.endel.demobox.repository.catalog.ProtectionRepository;
import com.endel.demobox.repository.catalog.TerminalRepository;
import com.endel.demobox.service.WiringSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KitDiagramServiceTest {

    private KitCompatibilityService compatibility;
    private KitDiagramService diagrams;
    private WiringSettingsDto lengths;
    private long nextId = 1;

    @BeforeEach
    void setUp() {
        ConnectionCableRepository cables = mock(ConnectionCableRepository.class);
        when(cables.findAll()).thenReturn(List.of());
        ProtectionRepository protections = mock(ProtectionRepository.class);
        when(protections.findAll()).thenReturn(List.of());
        TerminalRepository terminals = mock(TerminalRepository.class);
        when(terminals.findAll()).thenReturn(List.of());
        compatibility = new KitCompatibilityService(cables, new KitProtectionPlanner(protections), new KitTerminalPlanner(terminals));
        diagrams = new KitDiagramService(mock(KitRepository.class), compatibility, mock(WiringSettingsService.class));
        lengths = new WiringSettingsDto();
        lengths.setBatteryToInverterMeters(2.0);
        lengths.setPvToInverterMeters(10.0);
    }

    private KitDiagramDto diagram(List<KitLine> lines) {
        return diagrams.build("TEST Kit", compatibility.analyze(lines, lengths));
    }

    // ---- fixtures ----

    private Inverter inverter(double kw, double volts, boolean builtInCharger) {
        Inverter inverter = new Inverter();
        inverter.setId(nextId++);
        inverter.setName("Inversor " + kw + " kW");
        inverter.setRatedPowerKw(kw);
        inverter.setBatteryVoltage(volts);
        if (builtInCharger) {
            inverter.setInverterType(InverterType.HYBRID);
            inverter.setMpptChannels(1);
            inverter.setMaxInputVoltage(500);
            inverter.setMpptMinVoltage(120.0);
            inverter.setMpptMaxInputCurrentA(27.0);
            inverter.setMpptMaxChargeCurrentA(100.0);
        } else {
            inverter.setInverterType(InverterType.OFF_GRID);
            inverter.setBuiltInCharger(false);
        }
        return inverter;
    }

    private ChargeController controller(double chargeA, int volts) {
        ChargeController controller = new ChargeController();
        controller.setId(nextId++);
        controller.setName("PWM " + (int) chargeA + " A");
        controller.setMaxInputVoltage(50);
        controller.setMaxChargeCurrentA(chargeA);
        controller.setMpptMaxInputCurrentA(chargeA);
        controller.getSupportedBatteryVoltages().addAll(Set.of(volts));
        return controller;
    }

    private Battery battery(double volts, double ah) {
        Battery battery = new Battery();
        battery.setId(nextId++);
        battery.setName("LiFePO4 " + volts + " V " + ah + " Ah");
        battery.setVoltage(volts);
        battery.setCapacityAh(ah);
        battery.setChemistry(BatteryChemistry.LIFEPO4);
        return battery;
    }

    private SolarPanel panel(double watts, double voc, double isc) {
        SolarPanel panel = new SolarPanel();
        panel.setId(nextId++);
        panel.setName("Panel " + (int) watts + " W");
        panel.setWattage(watts);
        panel.setVoc(voc);
        panel.setIsc(isc);
        return panel;
    }

    private Protection protection(String name, ProtectionType type, FuseClass fuseClass, double amps, double volts, int poles) {
        Protection protection = new Protection();
        protection.setId(nextId++);
        protection.setSku(name);
        protection.setName(name);
        protection.setProtectionType(type);
        protection.setFuseClass(fuseClass);
        protection.setRatedCurrentA(amps);
        protection.setMaxVoltage(volts);
        protection.setPoles(poles);
        protection.setPrice(BigDecimal.ONE);
        protection.setActive(true);
        return protection;
    }

    /** The 12 V reference kit: 600 W inverter, 10 A controller, one 100 W panel, one 12.8 V battery, every protection in the kit. */
    private List<KitLine> twelveVoltKit(boolean withProtections) {
        List<KitLine> lines = new ArrayList<>(List.of(
                new KitLine(inverter(0.6, 12, false), 1),
                new KitLine(controller(10, 12), 1),
                new KitLine(battery(12.8, 100), 1),
                new KitLine(panel(100, 22, 6.5), 1)));
        if (withProtections) {
            Protection breaker = protection("RED WOLF 250 A", ProtectionType.DC_BREAKER, null, 250, 24, 1);
            breaker.setTerminalSize(TerminalSize.M10);
            lines.addAll(List.of(
                    new KitLine(protection("ANL 80 A", ProtectionType.FUSE, FuseClass.ANL, 80, 32, 1), 1),
                    new KitLine(breaker, 1),
                    new KitLine(protection("Fusible navaja 15 A", ProtectionType.FUSE, FuseClass.OTHER, 15, 32, 1), 1),
                    new KitLine(protection("Chtaixi C32", ProtectionType.PV_DISCONNECT, null, 32, 1000, 2), 1),
                    new KitLine(protection("Breaker AC 15 A", ProtectionType.AC_BREAKER, null, 15, 120, 1), 1)));
        }
        return lines;
    }

    // ---- tests ----

    @Test
    void separateControllerKitNumbersEveryElementAndTagsEveryLug() {
        KitDiagramDto d = diagram(twelveVoltKit(true));

        assertThat(d.isDrawable()).isTrue();
        assertThat(d.isHasController()).isTrue();
        assertThat(d.isBuiltInCharger()).isFalse();
        assertThat(d.getNums()).containsEntry("PANEL", "①").containsEntry("CONTROLLER", "④").containsEntry("BATTERY", "⑥")
                .containsEntry("MAIN_FUSE", "⑦").containsEntry("BATTERY_SWITCH", "⑧").containsEntry("INVERTER", "⑨").containsEntry("GROUND", "⑫");

        Map<String, String> tags = d.getTags();
        assertThat(tags).containsEntry("BAT_POS", "T1").containsEntry("FUSE_IN", "T2").containsEntry("FUSE_OUT", "T3")
                .containsEntry("SW_IN", "T4").containsEntry("SW_OUT", "T5").containsEntry("INV_POS", "T6")
                .containsEntry("BAT_NEG", "T7").containsEntry("INV_NEG", "T8")
                .containsEntry("CTL_PROT_IN", "T9").containsEntry("CTL_PROT_OUT", "T10")
                .containsEntry("CTL_BAT_POS", "T11").containsEntry("CTL_BAT_NEG", "T12")
                .containsEntry("GND_INV", "T13").containsEntry("GND_PANEL", "T14");
        assertThat(d.getTagRows()).anySatisfy(r -> {
            assertThat(r.ids()).isEqualTo("T4 / T5");
            assertThat(r.size()).isEqualTo("M10");
            assertThat(r.gauge()).isEqualTo("4 AWG");
        });

        assertThat(d.getMainFuse().getState()).isEqualTo("kit");
        assertThat(d.getMainFuse().getBody()).isEqualTo("80A");
        assertThat(d.getMainFuse().getLine()).isEqualTo("Fusible ANL 80 A · 32 V");
        assertThat(d.getBatterySwitch().getLine()).isEqualTo("Breaker DC 250 A · 24 V");
        assertThat(d.getAcLabels().get(0)).isEqualTo("AC: 14 AWG THHN");
        assertThat(d.getChassisGroundLabel()).isEqualTo("8 AWG verde");
        assertThat(d.getProtections()).allSatisfy(r -> assertThat(r.state()).isEqualTo("kit"));
        // Battery before panels on the controller.
        int controllerStep = indexOf(d.getConnectSteps(), "Controlador ④ → batería");
        int panelStep = indexOf(d.getConnectSteps(), "conecta los paneles");
        assertThat(controllerStep).isGreaterThanOrEqualTo(0).isLessThan(panelStep);
    }

    @Test
    void missingProtectionsAreFlaggedAndStillTagged() {
        KitDiagramDto d = diagram(twelveVoltKit(false));

        assertThat(d.getMainFuse().isMissing()).isTrue();
        assertThat(d.getMainFuse().getBody()).isEqualTo("?");
        assertThat(d.getMainFuse().getLine()).startsWith("FALTA");
        assertThat(d.getTagRows()).anySatisfy(r -> {
            assertThat(r.where()).startsWith("Fusible principal");
            assertThat(r.size()).isEqualTo("según la pieza");
        });
        assertThat(d.getProtections()).extracting(KitDiagramDto.ProtectionRow::state).containsOnly("missing");
        assertThat(d.getWarnings()).isNotEmpty();
    }

    @Test
    void allInOneInverterTakesThePanelsAndNeedsClassT() {
        KitDiagramDto d = diagram(List.of(
                new KitLine(inverter(5, 48, true), 1),
                new KitLine(battery(51.2, 100), 1),
                new KitLine(panel(550, 49.5, 14), 4)));

        assertThat(d.isDrawable()).isTrue();
        assertThat(d.isBuiltInCharger()).isTrue();
        assertThat(d.isHasController()).isFalse();
        assertThat(d.getNums()).doesNotContainKeys("CONTROLLER", "CONTROLLER_PROTECTION").containsEntry("BATTERY", "④");
        assertThat(d.getTags()).doesNotContainKeys("CTL_PROT_IN", "CTL_BAT_POS");
        assertThat(d.getInverterLines()).anyMatch(l -> l.startsWith("MPPT integrado"));
        assertThat(d.getPanelTitle()).isEqualTo("① Paneles: 4 × 550 W");
        assertThat(d.getRules()).anyMatch(r -> r.contains("Clase T"));
        // Panels go in once the inverter is on.
        assertThat(indexOf(d.getConnectSteps(), "Enciende")).isLessThan(indexOf(d.getConnectSteps(), "conecta los paneles"));
    }

    @Test
    void batteryBreakerReplacesFuseAndSwitch() {
        List<KitLine> lines = twelveVoltKit(false);
        Battery battery = (Battery) lines.get(2).product();
        battery.setIntegratedBreakerA(80.0);

        KitDiagramDto d = diagram(lines);

        assertThat(d.getMainFuse()).isNull();
        assertThat(d.getBatterySwitch()).isNull();
        assertThat(d.getNums()).doesNotContainKeys("MAIN_FUSE", "BATTERY_SWITCH");
        assertThat(d.getTags()).doesNotContainKeys("FUSE_IN", "SW_IN").containsEntry("INV_POS", "T2");
        assertThat(d.getBatteryLines()).anyMatch(l -> l.equals("breaker integrado 80 A: hace de switch y fusible"));
    }

    @Test
    void kitWithoutBatteriesCannotBeDrawn() {
        KitDiagramDto d = diagram(List.of(new KitLine(inverter(3, 48, true), 1), new KitLine(panel(550, 49.5, 14), 4)));

        assertThat(d.isDrawable()).isFalse();
        assertThat(d.getBlockers()).anyMatch(b -> b.contains("baterías"));
    }

    @Test
    void mrbfFuseHasNoLugsOfItsOwn() {
        List<KitLine> lines = twelveVoltKit(true);
        lines.set(4, new KitLine(protection("MRBF 80 A", ProtectionType.FUSE, FuseClass.MRBF, 80, 58, 1), 1));

        KitDiagramDto d = diagram(lines);

        assertThat(d.getMainFuse().getState()).isEqualTo("kit");
        assertThat(d.getTags()).doesNotContainKeys("FUSE_IN", "FUSE_OUT").containsEntry("SW_IN", "T2");
        assertThat(d.getRules()).anyMatch(r -> r.contains("MRBF se atornilla"));
    }

    private static int indexOf(List<String> steps, String fragment) {
        for (int i = 0; i < steps.size(); i++) {
            if (steps.get(i).contains(fragment)) {
                return i;
            }
        }
        return -1;
    }
}
