package com.endel.demobox.service.catalog;

import com.endel.demobox.model.catalog.AluminumStructure;
import com.endel.demobox.model.catalog.Battery;
import com.endel.demobox.model.catalog.BatteryChemistry;
import com.endel.demobox.model.catalog.ChargeController;
import com.endel.demobox.model.catalog.ConnectionCable;
import com.endel.demobox.model.catalog.Inverter;
import com.endel.demobox.model.catalog.InverterType;
import com.endel.demobox.model.catalog.PanelConnector;
import com.endel.demobox.model.dto.catalog.compatibility.SuggestedItemDto;
import com.endel.demobox.model.dto.catalog.compatibility.ProtectionCheckDto;
import com.endel.demobox.model.catalog.FuseClass;
import com.endel.demobox.model.catalog.ProtectionType;
import com.endel.demobox.model.catalog.Protection;
import com.endel.demobox.model.catalog.SolarPanel;
import com.endel.demobox.model.catalog.WireGauge;
import com.endel.demobox.model.dto.WiringSettingsDto;
import com.endel.demobox.model.dto.catalog.compatibility.CompatibilitySeverity;
import com.endel.demobox.model.dto.catalog.compatibility.ExpansionLevel;
import com.endel.demobox.model.dto.catalog.compatibility.KitCompatibilityReportDto;
import com.endel.demobox.model.dto.catalog.compatibility.WiringSegmentDto;
import com.endel.demobox.repository.catalog.ConnectionCableRepository;
import com.endel.demobox.repository.catalog.ProtectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KitCompatibilityServiceTest {

    private ConnectionCableRepository cableRepository;
    private KitCompatibilityService service;
    private WiringSettingsDto lengths;
    private ProtectionRepository protectionRepository;
    private long nextId = 1;

    @BeforeEach
    void setUp() {
        cableRepository = mock(ConnectionCableRepository.class);
        when(cableRepository.findAll()).thenReturn(List.of());
        protectionRepository = mock(ProtectionRepository.class);
        when(protectionRepository.findAll()).thenReturn(List.of());
        service = new KitCompatibilityService(cableRepository, new KitProtectionPlanner(protectionRepository));
        lengths = new WiringSettingsDto();
        lengths.setBatteryToInverterMeters(2.0);
        lengths.setPvToInverterMeters(10.0);
    }

    private Inverter inverter(double kw, Double batteryVolts, InverterType type) {
        Inverter inverter = new Inverter();
        inverter.setId(nextId++);
        inverter.setName("Inversor " + kw + "kW");
        inverter.setRatedPowerKw(kw);
        inverter.setInverterType(type);
        inverter.setBatteryVoltage(batteryVolts);
        inverter.setMpptChannels(1);
        inverter.setMaxInputVoltage(500);
        inverter.setMpptMinVoltage(120.0);
        inverter.setMpptMaxInputCurrentA(27.0);
        inverter.setMpptMaxChargeCurrentA(40.0);
        return inverter;
    }

    /** Inverter-only unit (no solar charger): the MPPT data comes from a separate controller. */
    private Inverter inverterWithoutCharger(double kw, double batteryVolts) {
        Inverter inverter = inverter(kw, batteryVolts, InverterType.OFF_GRID);
        inverter.setBuiltInCharger(false);
        inverter.setMpptChannels(null);
        inverter.setMaxInputVoltage(0);
        inverter.setMpptMinVoltage(null);
        inverter.setMpptMaxInputCurrentA(null);
        inverter.setMpptMaxChargeCurrentA(null);
        return inverter;
    }

    private ChargeController controller(double maxVolts, double chargeA, Integer... batteryVolts) {
        ChargeController controller = new ChargeController();
        controller.setId(nextId++);
        controller.setName("MPPT " + (int) maxVolts + "/" + (int) chargeA);
        controller.setMaxInputVoltage(maxVolts);
        controller.setMaxChargeCurrentA(chargeA);
        controller.setMpptMaxInputCurrentA(35.0);
        controller.getSupportedBatteryVoltages().addAll(Set.of(batteryVolts));
        return controller;
    }

    private Battery battery(double volts, double ah) {
        Battery battery = new Battery();
        battery.setId(nextId++);
        battery.setName("LiFePO4 " + volts + "V " + ah + "Ah");
        battery.setVoltage(volts);
        battery.setCapacityAh(ah);
        battery.setChemistry(BatteryChemistry.LIFEPO4);
        return battery;
    }

    private SolarPanel panel() {
        SolarPanel panel = new SolarPanel();
        panel.setId(nextId++);
        panel.setName("Panel 550W");
        panel.setWattage(550);
        panel.setVoc(49.5);
        panel.setIsc(13.9);
        return panel;
    }

    private ConnectionCable cable(String sku, WireGauge gauge, double meters) {
        ConnectionCable cable = new ConnectionCable();
        cable.setId(nextId++);
        cable.setSku(sku);
        cable.setName("Cable " + gauge.getLabel());
        cable.setGauge(gauge);
        cable.setLengthMeters(meters);
        cable.setPrice(BigDecimal.TEN);
        cable.setActive(true);
        return cable;
    }

    private static boolean hasCheck(KitCompatibilityReportDto report, CompatibilitySeverity severity, String fragment) {
        return report.getChecks().stream()
                .anyMatch(c -> c.getSeverity() == severity && c.getMessage().contains(fragment));
    }

    private static WiringSegmentDto segment(KitCompatibilityReportDto report, String prefix) {
        return report.getWiring().stream().filter(s -> s.getSegment().startsWith(prefix)).findFirst().orElseThrow();
    }


    // ---- Protections ----

    private Protection protection(String name, ProtectionType type, FuseClass fuseClass, double amps, double volts, int poles, int price) {
        Protection protection = new Protection();
        protection.setId(nextId++);
        protection.setSku(name);
        protection.setName(name);
        protection.setProtectionType(type);
        protection.setFuseClass(fuseClass);
        protection.setRatedCurrentA(amps);
        protection.setMaxVoltage(volts);
        protection.setPoles(poles);
        protection.setPrice(BigDecimal.valueOf(price));
        protection.setActive(true);
        return protection;
    }

    private final Protection mrbf110 = protection("MRBF 110 A 58 V", ProtectionType.FUSE, FuseClass.MRBF, 110, 58, 1, 350);
    private final Protection mrbf125 = protection("MRBF 125 A 58 V", ProtectionType.FUSE, FuseClass.MRBF, 125, 58, 1, 300);
    private final Protection anl110 = protection("ANL 110 A 32 V", ProtectionType.FUSE, FuseClass.ANL, 110, 32, 1, 200);
    private final Protection classT110 = protection("Clase T 110 A", ProtectionType.FUSE, FuseClass.CLASS_T, 110, 125, 1, 1500);
    private final Protection mrbf175 = protection("MRBF 175 A 58 V", ProtectionType.FUSE, FuseClass.MRBF, 175, 58, 1, 300);
    private final Protection classT175 = protection("Clase T 175 A", ProtectionType.FUSE, FuseClass.CLASS_T, 175, 125, 1, 1500);
    private final Protection dcBreaker125 = protection("Breaker DC 125 A 63 V", ProtectionType.DC_BREAKER, null, 125, 63, 1, 700);
    private final Protection dcBreaker200 = protection("Breaker DC 200 A 63 V", ProtectionType.DC_BREAKER, null, 200, 63, 1, 900);
    private final Protection pv500 = protection("Desconectador 2P 500 V", ProtectionType.PV_DISCONNECT, null, 32, 500, 2, 450);
    private final Protection pv150 = protection("Desconectador 2P 150 V", ProtectionType.PV_DISCONNECT, null, 32, 150, 2, 300);
    private final Protection ac40 = protection("Breaker AC 40 A con caja", ProtectionType.AC_BREAKER, null, 40, 120, 1, 400);
    private final Protection ac100 = protection("Breaker AC 100 A", ProtectionType.AC_BREAKER, null, 100, 120, 1, 250);
    private final Protection ac15 = protection("Breaker AC 15 A con caja", ProtectionType.AC_BREAKER, null, 15, 120, 1, 250);

    private void catalogHas(Protection... protections) {
        when(protectionRepository.findAll()).thenReturn(List.of(protections));
    }

    /** 3.5 kW / 48 V hybrid, 4 x 12.8 V 100 Ah LiFePO4 in series, 6 x 550 W panels in one string. */
    private List<KitLine> midSystem(KitLine... extra) {
        List<KitLine> lines = new java.util.ArrayList<>(List.of(
                new KitLine(inverter(3.5, 48.0, InverterType.HYBRID), 1),
                new KitLine(battery(12.8, 100), 4),
                new KitLine(panel(), 6)));
        lines.addAll(List.of(extra));
        return lines;
    }

    private static ProtectionCheckDto protectionRow(KitCompatibilityReportDto report, String label) {
        return report.getProtections().stream().filter(p -> p.getLabel().equals(label)).findFirst().orElseThrow();
    }

    @Test
    void midSystemNeedsFourProtections_andSuggestsTheCheapestThatQualifies() {
        catalogHas(mrbf110, mrbf125, anl110, classT110, dcBreaker125, pv500, pv150, ac40, ac100);
        // Battery run: 3500 / (48 x 0.9) = 81 A -> 101.3 A design -> 110 A fuse -> 2 AWG (115 A).
        KitCompatibilityReportDto report = service.analyze(midSystem(), lengths);

        assertEquals(List.of("Fusible principal de batería", "Interruptor / breaker DC de batería",
                        "Desconectador DC de paneles", "Breaker AC de salida del inversor"),
                report.getProtections().stream().map(ProtectionCheckDto::getLabel).toList());
        assertTrue(report.getProtections().stream().noneMatch(ProtectionCheckDto::isCovered));

        // ANL is cheaper but only takes 32 V; the 125 A MRBF is cheaper but more than the 2 AWG cable carries.
        assertEquals("de 101.3 a 115 A, 57.6 V DC o más", protectionRow(report, "Fusible principal de batería").getRequirement());
        assertEquals("MRBF 110 A 58 V", protectionRow(report, "Fusible principal de batería").getSuggestion().getName());
        assertEquals("Breaker DC 125 A 63 V", protectionRow(report, "Interruptor / breaker DC de batería").getSuggestion().getName());
        // Cold string voltage 6 x 49.5 x 1.25 = 371.3 V: the 150 V disconnect doesn't qualify.
        assertEquals("17.4 A o más, 371.3 V DC o más, 2 polos", protectionRow(report, "Desconectador DC de paneles").getRequirement());
        assertEquals("Desconectador 2P 500 V", protectionRow(report, "Desconectador DC de paneles").getSuggestion().getName());
        // 3500 W / 120 V x 1.25 = 36.5 A -> 40 A (or one size up, 45 A); 100 A would not protect the wiring.
        assertEquals("de 40 a 45 A, 120 V AC o más", protectionRow(report, "Breaker AC de salida del inversor").getRequirement());
        assertEquals("Breaker AC 40 A con caja", protectionRow(report, "Breaker AC de salida del inversor").getSuggestion().getName());

        assertEquals(List.of("MRBF 110 A 58 V", "Breaker DC 125 A 63 V", "Desconectador 2P 500 V", "Breaker AC 40 A con caja"),
                report.getSuggestedItems().stream().map(SuggestedItemDto::getName).toList());
        assertTrue(hasCheck(report, CompatibilitySeverity.WARNING,
                "Falta fusible principal de batería (de 101.3 a 115 A, 57.6 V DC o más). Sugerencia: MRBF 110 A 58 V."), report.getChecks().toString());
    }

    @Test
    void aProtectionThatDoesNotQualifyIsExplainedAndTheRightOneCoversIt() {
        catalogHas(mrbf110, anl110, dcBreaker125, pv500, ac40);
        KitCompatibilityReportDto withAnl = service.analyze(midSystem(new KitLine(anl110, 1)), lengths);
        ProtectionCheckDto fuse = protectionRow(withAnl, "Fusible principal de batería");
        assertFalse(fuse.isCovered());
        assertEquals("ANL 110 A 32 V: aguanta 32 V y se necesitan 57.6 V o más", fuse.getProblem());
        assertTrue(hasCheck(withAnl, CompatibilitySeverity.WARNING, "1 × ANL 110 A 32 V no corresponde a ninguna protección"),
                withAnl.getChecks().toString());

        KitCompatibilityReportDto complete = service.analyze(midSystem(new KitLine(mrbf110, 1), new KitLine(dcBreaker125, 1),
                new KitLine(pv500, 1), new KitLine(ac40, 1)), lengths);
        assertTrue(complete.getProtections().stream().allMatch(ProtectionCheckDto::isCovered), complete.getProtections().toString());
        assertEquals("1 × MRBF 110 A 58 V", protectionRow(complete, "Fusible principal de batería").getCoveredBy());
        assertTrue(complete.getSuggestedItems().isEmpty());
        assertFalse(hasCheck(complete, CompatibilitySeverity.WARNING, "Falta"), complete.getChecks().toString());
    }

    @Test
    void lithiumBatteriesInParallelNeedAClassTFuse() {
        catalogHas(mrbf175, classT175, dcBreaker200);
        // 3000 / (24 x 0.9) = 138.9 A -> 173.6 A design -> 175 A fuse -> 2/0 (175 A). 4 x 12.8 V on 24 V = 2S x 2P.
        KitCompatibilityReportDto report = service.analyze(List.of(
                new KitLine(inverter(3.0, 24.0, InverterType.HYBRID), 1),
                new KitLine(battery(12.8, 100), 4)), lengths);

        ProtectionCheckDto fuse = protectionRow(report, "Fusible principal de batería");
        assertEquals("de 173.6 a 175 A, 28.8 V DC o más, Clase T", fuse.getRequirement());
        assertEquals("Clase T 175 A", fuse.getSuggestion().getName()); // the cheaper MRBF can't interrupt a parallel lithium bank
    }

    @Test
    void aBatteryWithABuiltInBreakerNeedsNoSeparateFuseOrSwitch() {
        catalogHas(mrbf110, dcBreaker125, pv500, ac40);
        Battery rack = battery(51.2, 100);
        rack.setIntegratedBreakerA(110.0);
        KitCompatibilityReportDto report = service.analyze(List.of(
                new KitLine(inverter(3.5, 48.0, InverterType.HYBRID), 1), new KitLine(rack, 1)), lengths);

        assertEquals("Breaker integrado de la batería (110 A)", protectionRow(report, "Fusible principal de batería").getCoveredBy());
        assertTrue(protectionRow(report, "Desconexión de batería").isCovered());
        assertFalse(report.getProtections().stream().anyMatch(p -> p.getLabel().startsWith("Interruptor")));
        assertFalse(report.getSuggestedItems().stream().anyMatch(i -> i.getName().contains("MRBF") || i.getName().contains("Breaker DC")));

        // A 125 A breaker is more than the 2 AWG cable (115 A) carries: the cable still needs its own fuse.
        rack.setIntegratedBreakerA(125.0);
        KitCompatibilityReportDto bigBreaker = service.analyze(List.of(
                new KitLine(inverter(3.5, 48.0, InverterType.HYBRID), 1), new KitLine(rack, 1)), lengths);
        assertFalse(protectionRow(bigBreaker, "Fusible principal de batería").isCovered());
        assertTrue(protectionRow(bigBreaker, "Desconexión de batería").isCovered());
    }

    @Test
    void smallInverterGetsASmallAcBreaker_andAnEmptyCatalogSaysToAddOne() {
        catalogHas(ac15);
        KitCompatibilityReportDto report = service.analyze(List.of(
                new KitLine(inverter(1.0, 12.0, InverterType.OFF_GRID), 1),
                new KitLine(battery(12.8, 100), 1)), lengths);
        // 1000 W / 120 V x 1.25 = 10.4 A -> 15 A breaker.
        assertEquals("Breaker AC 15 A con caja", protectionRow(report, "Breaker AC de salida del inversor").getSuggestion().getName());
        assertTrue(hasCheck(report, CompatibilitySeverity.WARNING,
                "No hay en tu catálogo una protección que cumpla; dala de alta en Protecciones."), report.getChecks().toString());
    }

    @Test
    void oneKilowattInverterOnTwelveVolts_needsOneOughtCableAndA125AmpFuse() {
        // 1000 W / (12 V x 0.9) = 92.6 A operating; x1.25 = 115.7 A -> 125 A fuse. The cable must carry the fuse:
        // 2 AWG (115 A) is too small, 1/0 (150 A, NEC 75 C) fits. The 100 Ah LiFePO4 gives 100 A, so it doesn't limit.
        Inverter inverter = inverter(1.0, 12.0, InverterType.OFF_GRID);
        KitCompatibilityReportDto report = service.analyze(List.of(
                new KitLine(inverter, 1),
                new KitLine(battery(12.8, 100), 1)), lengths);

        WiringSegmentDto batteryRun = segment(report, "Batería");
        assertEquals(92.6, batteryRun.getOperatingCurrentA(), 0.1);
        assertEquals(WireGauge.AWG_1_0, batteryRun.getGauge());
        assertEquals(125, batteryRun.getFuseA());
        assertTrue(batteryRun.getVoltageDropPercent() < 3.0);
        assertTrue(report.getBatteryBank().startsWith("1 en serie × 1 en paralelo"));
    }

    @Test
    void smallLithiumBankWithoutBmsRating_cableSizedForItsCapacityInAmps_withWarnings() {
        // The inverter could pull 92.6 A, but a 50 Ah LiFePO4 is assumed to give 50 A (1C) before its BMS cuts off:
        // 50 x 1.25 = 62.5 A -> 70 A fuse -> 4 AWG (85 A) instead of 1/0.
        KitCompatibilityReportDto report = service.analyze(List.of(
                new KitLine(inverter(1.0, 12.0, InverterType.OFF_GRID), 1),
                new KitLine(battery(12.8, 50), 1)), lengths);

        WiringSegmentDto batteryRun = segment(report, "Batería");
        assertEquals(50.0, batteryRun.getOperatingCurrentA(), 0.01);
        assertEquals(70, batteryRun.getFuseA());
        assertEquals(WireGauge.AWG_4, batteryRun.getGauge());
        assertEquals("Limitada por el BMS del banco (50 A; el inversor podría pedir 92.6 A)", batteryRun.getCurrentNote());
        assertTrue(hasCheck(report, CompatibilitySeverity.WARNING,
                "El cable batería–inversor se calculó con esos 50 A, porque el BMS corta antes."), report.getChecks().toString());
        assertTrue(hasCheck(report, CompatibilitySeverity.WARNING,
                "no tiene capturada la corriente máxima de su BMS; se supuso 50 A"), report.getChecks().toString());
    }

    @Test
    void capturedBmsRatingIsUsedAndTheAssumptionWarningGoesAway() {
        Battery battery = battery(12.8, 50);
        battery.setBmsMaxDischargeA(60.0);
        KitCompatibilityReportDto report = service.analyze(List.of(
                new KitLine(inverter(1.0, 12.0, InverterType.OFF_GRID), 1),
                new KitLine(battery, 1)), lengths);

        WiringSegmentDto batteryRun = segment(report, "Batería");
        assertEquals(60.0, batteryRun.getOperatingCurrentA(), 0.01); // 75 A design -> 80 A fuse -> 4 AWG
        assertEquals(80, batteryRun.getFuseA());
        assertEquals(WireGauge.AWG_4, batteryRun.getGauge());
        assertFalse(hasCheck(report, CompatibilitySeverity.WARNING, "no tiene capturada"), report.getChecks().toString());
    }

    @Test
    void leadAcidHasNoBms_soTheCableIsSizedForTheFullInverterDraw() {
        Battery agm = battery(12.0, 50);
        agm.setChemistry(BatteryChemistry.AGM);
        agm.setBmsMaxDischargeA(30.0); // meaningless without a BMS: ignored
        KitCompatibilityReportDto report = service.analyze(List.of(
                new KitLine(inverter(1.0, 12.0, InverterType.OFF_GRID), 1),
                new KitLine(agm, 1)), lengths);

        WiringSegmentDto batteryRun = segment(report, "Batería");
        assertEquals(92.6, batteryRun.getOperatingCurrentA(), 0.1);
        assertEquals(WireGauge.AWG_1_0, batteryRun.getGauge());
        assertNull(batteryRun.getCurrentNote());
        assertEquals(10.0, agm.getMaxDischargeCurrentA(), 0.01); // 0.2C, only used for the "bank gives max" warning
        assertFalse(hasCheck(report, CompatibilitySeverity.WARNING, "se calculó con esos"), report.getChecks().toString());
        assertFalse(hasCheck(report, CompatibilitySeverity.WARNING, "no tiene capturada"), report.getChecks().toString());
    }

    @Test
    void twelveVoltBatteriesAreSeriesWiredToMatchAFortyEightVoltInverter() {
        KitCompatibilityReportDto report = service.analyze(List.of(
                new KitLine(inverter(5.0, 48.0, InverterType.HYBRID), 1),
                new KitLine(battery(12.8, 100), 8)), lengths);

        assertTrue(report.getBatteryBank().startsWith("4 en serie × 2 en paralelo · 51.2 V, 200 Ah"), report.getBatteryBank());
        assertTrue(hasCheck(report, CompatibilitySeverity.OK, "4S2P"));
    }

    @Test
    void batteryCountThatCannotFillTheSeriesStringIsAnError() {
        KitCompatibilityReportDto report = service.analyze(List.of(
                new KitLine(inverter(5.0, 48.0, InverterType.HYBRID), 1),
                new KitLine(battery(12.8, 100), 3)), lengths);

        assertEquals(CompatibilitySeverity.ERROR, report.getStatus());
        assertTrue(hasCheck(report, CompatibilitySeverity.ERROR, "múltiplos de 4"));
        assertNull(report.getBatteryBank());
    }

    @Test
    void batteryWithHigherVoltageThanTheInverterIsAnError() {
        KitCompatibilityReportDto report = service.analyze(List.of(
                new KitLine(inverter(1.0, 12.0, InverterType.OFF_GRID), 1),
                new KitLine(battery(51.2, 100), 1)), lengths);

        assertTrue(hasCheck(report, CompatibilitySeverity.ERROR, "no se pueden combinar"));
    }

    @Test
    void gridTieInverterWithBatteriesIsAnError() {
        KitCompatibilityReportDto report = service.analyze(List.of(
                new KitLine(inverter(5.0, 48.0, InverterType.STRING), 1),
                new KitLine(battery(51.2, 100), 1)), lengths);

        assertTrue(hasCheck(report, CompatibilitySeverity.ERROR, "no admite baterías"));
    }

    @Test
    void panelsAreArrangedIntoTheLongestEvenStringWithinTheMpptWindow() {
        // cold Voc 61.9 V -> max 8 in series (500 V); Vmp ~39.6 V -> min 4 in series (120 V). 6 panels -> 1 x 6.
        KitCompatibilityReportDto report = service.analyze(List.of(
                new KitLine(inverter(5.0, 48.0, InverterType.HYBRID), 1),
                new KitLine(panel(), 6)), lengths);

        assertTrue(report.getPanelArray().startsWith("1 cadena de 6 paneles en serie"), report.getPanelArray());
        // 13.9 A x 1.56 (NEC 690.8) = 21.7 A -> 25 A fuse -> 10 AWG (30 A); 12 AWG (20 A) is too small.
        assertEquals(WireGauge.AWG_10, segment(report, "Paneles").getGauge());
    }

    @Test
    void tooFewPanelsToReachTheMpptStartVoltageIsAnError() {
        KitCompatibilityReportDto report = service.analyze(List.of(
                new KitLine(inverter(5.0, 48.0, InverterType.HYBRID), 1),
                new KitLine(panel(), 2)), lengths);

        assertTrue(hasCheck(report, CompatibilitySeverity.ERROR, "al menos 4 paneles en serie"));
    }

    @Test
    void panelVocAboveTheMpptMaximumIsAnError() {
        Inverter inverter = inverter(1.0, 12.0, InverterType.OFF_GRID);
        inverter.setMaxInputVoltage(50);
        inverter.setMpptMinVoltage(null);
        KitCompatibilityReportDto report = service.analyze(List.of(
                new KitLine(inverter, 1),
                new KitLine(panel(), 1)), lengths);

        assertTrue(hasCheck(report, CompatibilitySeverity.ERROR, "supera el máximo del MPPT"));
    }

    @Test
    void panelCountThatCannotBeSplitEvenlyIsAWarning() {
        // 9 panels with 4..8 in series: no divisor fits.
        KitCompatibilityReportDto report = service.analyze(List.of(
                new KitLine(inverter(5.0, 48.0, InverterType.HYBRID), 1),
                new KitLine(panel(), 9)), lengths);

        assertTrue(hasCheck(report, CompatibilitySeverity.WARNING, "no se reparten en cadenas iguales"));
    }

    // 3600 W / 24 V all-in-one: 120 A MPPT, 60-500 V window, datasheet allows 4200 W of PV.
    private Inverter allInOne3600(Double maxPvInputPowerW) {
        Inverter inverter = inverter(3.6, 24.0, InverterType.HYBRID);
        inverter.setMpptMinVoltage(60.0);
        inverter.setMpptMaxChargeCurrentA(120.0);
        inverter.setMaxPvInputPowerW(maxPvInputPowerW);
        return inverter;
    }

    @Test
    void withoutDatasheetPvPower_overPanelingIsEstimatedFromChargePower() {
        // 7 x 550 W = 3850 W > 120 A x 24 V x 1.3 = 3744 W
        KitCompatibilityReportDto report = service.analyze(List.of(
                new KitLine(allInOne3600(null), 1),
                new KitLine(panel(), 7)), lengths);

        assertTrue(hasCheck(report, CompatibilitySeverity.WARNING, "potencia PV máxima estimada del inversor (~3744 W"));
    }

    @Test
    void datasheetPvPowerReplacesTheEstimate() {
        // Same 3850 W array is within the 4200 W the manufacturer allows.
        KitCompatibilityReportDto report = service.analyze(List.of(
                new KitLine(allInOne3600(4200.0), 1),
                new KitLine(panel(), 7)), lengths);

        assertTrue(report.getChecks().stream().noneMatch(c -> c.getMessage().contains("supera")), report.getChecks().toString());
    }

    @Test
    void arrayAboveDatasheetPvPowerIsAWarning() {
        // 8 x 550 W = 4400 W > 4200 W
        KitCompatibilityReportDto report = service.analyze(List.of(
                new KitLine(allInOne3600(4200.0), 1),
                new KitLine(panel(), 8)), lengths);

        assertTrue(hasCheck(report, CompatibilitySeverity.WARNING, "potencia PV máxima del inversor (4200 W según ficha)"));
    }

    @Test
    void inverterPowerTypedInWattsIsFlaggedAsImplausible() {
        // 1000 entered in the kW field instead of 1.
        KitCompatibilityReportDto report = service.analyze(List.of(
                new KitLine(inverter(1000, 12.0, InverterType.OFF_GRID), 1),
                new KitLine(battery(12.8, 50), 1)), lengths);

        assertTrue(hasCheck(report, CompatibilitySeverity.WARNING, "Los inversores suman 1000 kW"));
    }

    @Test
    void batteryBankAboveThirtyKwhIsFlaggedButThirtyOrLessIsNot() {
        // 6 x 51.2 V x 100 Ah = 30.72 kWh; 5 x = 25.6 kWh
        KitCompatibilityReportDto tooBig = service.analyze(List.of(
                new KitLine(inverter(5.0, 48.0, InverterType.HYBRID), 1),
                new KitLine(battery(51.2, 100), 6)), lengths);
        KitCompatibilityReportDto fine = service.analyze(List.of(
                new KitLine(inverter(5.0, 48.0, InverterType.HYBRID), 1),
                new KitLine(battery(51.2, 100), 5)), lengths);

        assertTrue(hasCheck(tooBig, CompatibilitySeverity.WARNING, "El banco de baterías suma 30.7 kWh"));
        assertTrue(fine.getChecks().stream().noneMatch(c -> c.getMessage().contains("demasiado")));
    }

    @Test
    void panelArrayAboveThirtyKwpIsFlaggedEvenWithoutAnInverter() {
        // 56 x 550 W = 30.8 kWp
        KitCompatibilityReportDto report = service.analyze(List.of(new KitLine(panel(), 56)), lengths);

        assertTrue(hasCheck(report, CompatibilitySeverity.WARNING, "Los paneles suman 30.8 kWp"));
    }

    @Test
    void twoBatteryModelsAreAnErrorAndStopTheAnalysis() {
        KitCompatibilityReportDto report = service.analyze(List.of(
                new KitLine(inverter(5.0, 48.0, InverterType.HYBRID), 1),
                new KitLine(battery(12.8, 100), 4),
                new KitLine(battery(12.8, 200), 4)), lengths);

        assertTrue(hasCheck(report, CompatibilitySeverity.ERROR, "un modelo de batería; este trae 2"));
        assertNull(report.getBatteryBank());
        assertTrue(report.getWiring().isEmpty());
    }

    @Test
    void twoPanelModelsAreAnError() {
        assertTrue(service.compositionErrors(List.of(
                new KitLine(panel(), 4),
                new KitLine(panel(), 2))).get(0).contains("un modelo de panel"));
    }

    @Test
    void moreThanOneInverterUnitOrModelIsAnError() {
        Inverter inverter = inverter(3.6, 24.0, InverterType.HYBRID);
        assertTrue(service.compositionErrors(List.of(new KitLine(inverter, 2))).get(0).contains("cantidad 1"));
        assertTrue(service.compositionErrors(List.of(
                new KitLine(inverter, 1),
                new KitLine(inverter(5.0, 48.0, InverterType.HYBRID), 1))).get(0).contains("2 modelos"));
    }

    @Test
    void extrasCanRepeatAndOneOfEachMainComponentIsValid() {
        AluminumStructure structure = new AluminumStructure();
        structure.setId(nextId++);
        structure.setName("Estructura");
        assertTrue(service.compositionErrors(List.of(
                new KitLine(inverter(5.0, 48.0, InverterType.HYBRID), 1),
                new KitLine(battery(12.8, 100), 8),
                new KitLine(panel(), 6),
                new KitLine(cable("C-1", WireGauge.AWG_2_0, 2), 2),
                new KitLine(cable("C-2", WireGauge.AWG_10, 20), 1),
                new KitLine(structure, 1),
                new KitLine(structure, 1))).isEmpty());
    }

    // ---- Expansion ----

    // 1000 W / 12 V with a 40 A MPPT: 480 W charge, ~624 W estimated PV limit,
    // recharges min(624, 480) W x 6 h x 0.8 = 2.3 kWh/day.
    private Inverter small1000W12V() {
        Inverter inverter = inverter(1.0, 12.0, InverterType.OFF_GRID);
        inverter.setMaxInputVoltage(100);
        inverter.setMpptMinVoltage(null);
        inverter.setMpptMaxChargeCurrentA(40.0);
        return inverter;
    }

    private SolarPanel panel200W() {
        SolarPanel panel = new SolarPanel();
        panel.setId(nextId++);
        panel.setName("Panel 200W");
        panel.setWattage(200);
        panel.setVoc(24);
        panel.setIsc(10);
        return panel;
    }

    @Test
    void smallTwelveVoltKitCanOnlyTakeOneMoreBattery() {
        // 12.8 V 100 Ah LiFePO4 = 1.15 kWh usable -> the 2.3 kWh/day recharge covers 2 -> +1 (limited, not doubling+).
        // Panels: 624 W / 200 W = 3 max (cold Voc 30 V -> up to 3 in series under 100 V) -> 1 + 2 (expandable).
        KitCompatibilityReportDto report = service.analyze(List.of(
                new KitLine(small1000W12V(), 1),
                new KitLine(battery(12.8, 100), 1),
                new KitLine(panel200W(), 1)), lengths);

        assertEquals(ExpansionLevel.LIMITED, report.getExpansion().getBatteries().getLevel());
        assertEquals(1, report.getExpansion().getBatteries().getAdditional());
        assertEquals(ExpansionLevel.EXPANDABLE, report.getExpansion().getPanels().getLevel());
        assertEquals(2, report.getExpansion().getPanels().getAdditional());
        assertEquals(ExpansionLevel.LIMITED, report.getExpansion().getLevel());
        assertEquals("Ampliación limitada", report.getExpansion().getLabel());
        assertTrue(report.getExpansion().getBatteries().getDetail().contains("Hasta 1 batería más"));
    }

    @Test
    void kitAlreadyAtBothLimitsHasNoRoomToGrow() {
        KitCompatibilityReportDto report = service.analyze(List.of(
                new KitLine(small1000W12V(), 1),
                new KitLine(battery(12.8, 100), 2),
                new KitLine(panel200W(), 3)), lengths);

        assertEquals(ExpansionLevel.MAXED, report.getExpansion().getBatteries().getLevel());
        assertEquals(ExpansionLevel.MAXED, report.getExpansion().getPanels().getLevel());
        assertEquals("Sin margen de ampliación", report.getExpansion().getLabel());
    }

    @Test
    void largeHybridWithOneBatteryAndFewPanelsIsExpandable() {
        // 5 kW / 48 V, 100 A MPPT: 4800 W charge, ~6240 W PV -> 23 kWh/day; 51.2 V 100 Ah = 4.6 kWh usable -> 5 strings.
        Inverter inverter = inverter(5.0, 48.0, InverterType.HYBRID);
        inverter.setMpptMaxChargeCurrentA(100.0);
        inverter.setMpptMaxInputCurrentA(30.0);
        KitCompatibilityReportDto report = service.analyze(List.of(
                new KitLine(inverter, 1),
                new KitLine(battery(51.2, 100), 1),
                new KitLine(panel(), 4)), lengths);

        assertEquals(4, report.getExpansion().getBatteries().getAdditional());
        // 6240 W / 550 W = 11 max; 11 and 9 don't split into valid strings, 10 = 2 x 5 does.
        assertEquals(6, report.getExpansion().getPanels().getAdditional());
        assertEquals("Ampliable", report.getExpansion().getLabel());
    }

    @Test
    void expansionIsOmittedWhenChargeDataIsMissing() {
        Inverter inverter = small1000W12V();
        inverter.setMpptMaxChargeCurrentA(null);
        KitCompatibilityReportDto report = service.analyze(List.of(
                new KitLine(inverter, 1),
                new KitLine(battery(12.8, 100), 1)), lengths);

        assertNull(report.getExpansion());
    }

    @Test
    void expansionIsOmittedForAnInvalidKit() {
        KitCompatibilityReportDto report = service.analyze(List.of(
                new KitLine(inverter(5.0, 48.0, InverterType.HYBRID), 1),
                new KitLine(battery(12.8, 100), 3)), lengths);

        assertNull(report.getExpansion());
    }

    @Test
    void kitCableOfTheRightGaugeAndLengthCoversTheBatteryRun() {
        KitCompatibilityReportDto report = service.analyze(List.of(
                new KitLine(inverter(1.0, 12.0, InverterType.OFF_GRID), 1),
                new KitLine(battery(12.8, 100), 1),
                new KitLine(cable("CBL-20", WireGauge.AWG_2_0, 2), 2)), lengths);

        // 2 m one-way x 2 conductors = 4 m needed, kit has 2 x 2 m.
        assertEquals("Cable 2/0 AWG", segment(report, "Batería").getCoveredBy());
        assertTrue(hasCheck(report, CompatibilitySeverity.OK, "cable suficiente"));
    }

    @Test
    void tooThinKitCableIsFlaggedWithACatalogSuggestion() {
        ConnectionCable thick = cable("CBL-40", WireGauge.AWG_4_0, 3);
        when(cableRepository.findAll()).thenReturn(List.of(thick));

        KitCompatibilityReportDto report = service.analyze(List.of(
                new KitLine(inverter(1.0, 12.0, InverterType.OFF_GRID), 1),
                new KitLine(battery(12.8, 100), 1),
                new KitLine(cable("CBL-4", WireGauge.AWG_4, 10), 1)), lengths);

        assertTrue(hasCheck(report, CompatibilitySeverity.WARNING, "Sugerencia: CBL-40"));
        assertNull(segment(report, "Batería").getCoveredBy());
    }

    @Test
    void missingInverterBatteryVoltageIsReportedInsteadOfFailing() {
        KitCompatibilityReportDto report = service.analyze(List.of(
                new KitLine(inverter(1.0, null, InverterType.OFF_GRID), 1),
                new KitLine(battery(12.8, 100), 1)), lengths);

        assertTrue(hasCheck(report, CompatibilitySeverity.WARNING, "Falta capturar el voltaje de baterías"));
        assertTrue(report.getWiring().isEmpty());
    }

    // ---- Separate charge controller ----

    @Test
    void inverterWithoutCharger_usesTheControllersLimitsAndGetsAControllerBatteryRun() {
        lengths.setControllerToBatteryMeters(1.5);
        KitCompatibilityReportDto report = service.analyze(List.of(
                new KitLine(inverterWithoutCharger(3.0, 48.0), 1),
                new KitLine(controller(250, 60, 12, 24, 48), 1),
                new KitLine(battery(51.2, 100), 2),
                new KitLine(panel(), 6)), lengths);

        assertTrue(report.getStatus() != CompatibilitySeverity.ERROR, report.getChecks().toString());
        assertEquals("Controlador MPPT 250/60 · 60 A de carga", report.getSolarCharger());
        assertTrue(hasCheck(report, CompatibilitySeverity.OK, "El controlador MPPT 250/60 carga el banco de 48 V"));
        // Max 250 V / (49.5 V x 1.25 cold) = 4 in series; 6 panels split evenly into 2 strings of 3.
        assertTrue(report.getPanelArray().startsWith("2 cadenas de 3 paneles en serie"), report.getPanelArray());

        // The inverter cable only carries discharge (3000 W / (48 V x 0.9) = 69.4 A); the controller has its own run.
        assertEquals(69.4, segment(report, "Batería – inversor").getOperatingCurrentA(), 0.1);
        WiringSegmentDto controllerRun = segment(report, "Controlador – batería");
        assertEquals(60.0, controllerRun.getOperatingCurrentA(), 0.1);
        assertEquals(1.5, controllerRun.getLengthMeters());
        assertNotNull(controllerRun.getGauge());
        segment(report, "Paneles – controlador MPPT");

        // Expansion is measured against the controller: 48 V x 60 A = 2880 W charge, ~3744 W PV estimated.
        assertNotNull(report.getExpansion());
        assertTrue(report.getExpansion().getPanels().getDetail().contains("3744 W estimado"), report.getExpansion().getPanels().getDetail());
        assertTrue(report.getExpansion().getBatteries().getDetail().contains("el controlador recarga"), report.getExpansion().getBatteries().getDetail());
    }

    @Test
    void controllerPvLimitsDriveTheWarnings() {
        ChargeController small = controller(100, 30, 12, 24, 48);
        KitCompatibilityReportDto report = service.analyze(List.of(
                new KitLine(inverterWithoutCharger(3.0, 48.0), 1),
                new KitLine(small, 1),
                new KitLine(battery(51.2, 100), 1),
                new KitLine(panel(), 4)), lengths);

        // 4 x 550 W = 2200 W over 48 V x 30 A x 1.3 = 1872 W estimated for the controller.
        assertTrue(hasCheck(report, CompatibilitySeverity.WARNING, "potencia PV máxima estimada del controlador (~1872 W"), report.getChecks().toString());
    }

    @Test
    void inverterWithoutChargerAndPanelsButNoController_isAnError() {
        KitCompatibilityReportDto report = service.analyze(List.of(
                new KitLine(inverterWithoutCharger(3.0, 48.0), 1),
                new KitLine(battery(51.2, 100), 1),
                new KitLine(panel(), 4)), lengths);

        assertEquals(CompatibilitySeverity.ERROR, report.getStatus());
        assertTrue(hasCheck(report, CompatibilitySeverity.ERROR, "no tiene cargador solar; agrega un controlador de carga MPPT"));
        assertNull(report.getPanelArray());
        assertNull(report.getExpansion());
    }

    @Test
    void inverterWithoutChargerAndNoPanels_onlyNeedsTheBatteries() {
        KitCompatibilityReportDto report = service.analyze(List.of(
                new KitLine(inverterWithoutCharger(3.0, 48.0), 1),
                new KitLine(battery(51.2, 100), 1)), lengths);

        assertTrue(report.getChecks().stream().noneMatch(c -> c.getSeverity() == CompatibilitySeverity.ERROR), report.getChecks().toString());
        assertNull(report.getSolarCharger());
        assertEquals(1, report.getWiring().size());
    }

    @Test
    void controllerThatCannotChargeTheBankVoltage_isAnError() {
        KitCompatibilityReportDto report = service.analyze(List.of(
                new KitLine(inverterWithoutCharger(3.0, 48.0), 1),
                new KitLine(controller(150, 40, 12, 24), 1),
                new KitLine(battery(51.2, 100), 1),
                new KitLine(panel(), 2)), lengths);

        assertTrue(hasCheck(report, CompatibilitySeverity.ERROR, "no admite baterías de 48 V (admite 12/24 V)"), report.getChecks().toString());
    }

    @Test
    void inverterWithBuiltInCharger_ignoresAControllerWithAWarning() {
        KitCompatibilityReportDto report = service.analyze(List.of(
                new KitLine(inverter(5.0, 48.0, InverterType.HYBRID), 1),
                new KitLine(controller(250, 60, 48), 1),
                new KitLine(battery(51.2, 100), 1),
                new KitLine(panel(), 6)), lengths);

        assertTrue(hasCheck(report, CompatibilitySeverity.WARNING, "ya trae cargador solar (MPPT); se usa el suyo y el controlador MPPT 250/60 no se toma en cuenta"));
        assertEquals("Integrado en el inversor · 40 A de carga", report.getSolarCharger());
        assertFalse(report.getWiring().stream().anyMatch(s -> s.getSegment().startsWith("Controlador")));
        segment(report, "Paneles – inversor (MPPT)");
    }

    @Test
    void panelCableNamesThePanelsConnector_mc4UnlessThePanelSaysOtherwise() {
        List<KitLine> base = List.of(
                new KitLine(inverter(5.0, 48.0, InverterType.HYBRID), 1),
                new KitLine(battery(51.2, 100), 1));

        KitCompatibilityReportDto mc4 = service.analyze(withPanels(base, panel()), lengths);
        assertEquals("MC4", segment(mc4, "Paneles – inversor (MPPT)").getConnector());

        SolarPanel saePanel = panel();
        saePanel.setConnector(PanelConnector.SAE);
        KitCompatibilityReportDto sae = service.analyze(withPanels(base, saePanel), lengths);
        assertEquals("SAE", segment(sae, "Paneles – inversor (MPPT)").getConnector());

        // Panels saved before the connector column existed have none stored: they read as MC4.
        SolarPanel legacy = panel();
        legacy.setConnector(null);
        assertEquals(PanelConnector.MC4, legacy.getConnector());

        // Only the panels' cable has a known connector.
        mc4.getWiring().stream()
                .filter(s -> !s.getSegment().startsWith("Paneles"))
                .forEach(s -> assertNull(s.getConnector(), s.getSegment()));
    }

    private static List<KitLine> withPanels(List<KitLine> base, SolarPanel panel) {
        List<KitLine> lines = new java.util.ArrayList<>(base);
        lines.add(new KitLine(panel, 6));
        return lines;
    }

    @Test
    void onlyOneChargeControllerPerKit() {
        ChargeController controller = controller(250, 60, 48);
        assertTrue(service.compositionErrors(List.of(new KitLine(controller, 2))).get(0)
                .contains("solo puede tener un controlador de carga (cantidad 1)"));
        assertTrue(service.compositionErrors(List.of(new KitLine(controller, 1), new KitLine(controller(150, 40, 48), 1))).get(0)
                .contains("solo puede tener un controlador de carga; este trae 2 modelos"));
    }

    @Test
    void inverterSavedBeforeTheChargerFlagExisted_countsAsAllInOne() {
        Inverter legacy = inverter(5.0, 48.0, InverterType.HYBRID);
        legacy.setBuiltInCharger(null);
        assertTrue(legacy.hasBuiltInCharger());
    }

    @Test
    void highBatteryCurrentSuggestsOnlyHigherBankVoltages() {
        // 3.3 kW / (24 V x 0.9) = 152.8 A
        KitCompatibilityReportDto at24 = service.analyze(List.of(
                new KitLine(inverter(3.3, 24.0, InverterType.OFF_GRID), 1),
                new KitLine(battery(25.6, 100), 2)), lengths);
        assertTrue(hasCheck(at24, CompatibilitySeverity.WARNING, "llega a 152.8 A en 24 V; considera un banco de 48 V."), at24.getChecks().toString());

        // 2 kW / (12 V x 0.9) = 185.2 A
        KitCompatibilityReportDto at12 = service.analyze(List.of(
                new KitLine(inverter(2.0, 12.0, InverterType.OFF_GRID), 1),
                new KitLine(battery(12.8, 200), 1)), lengths);
        assertTrue(hasCheck(at12, CompatibilitySeverity.WARNING, "llega a 185.2 A en 12 V; considera un banco de 24 V o 48 V."), at12.getChecks().toString());
    }
}
