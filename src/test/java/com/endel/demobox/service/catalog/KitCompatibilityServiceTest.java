package com.endel.demobox.service.catalog;

import com.endel.demobox.model.catalog.AluminumStructure;
import com.endel.demobox.model.catalog.Battery;
import com.endel.demobox.model.catalog.BatteryChemistry;
import com.endel.demobox.model.catalog.ChargeController;
import com.endel.demobox.model.catalog.ConnectionCable;
import com.endel.demobox.model.catalog.Inverter;
import com.endel.demobox.model.catalog.InverterType;
import com.endel.demobox.model.catalog.SolarPanel;
import com.endel.demobox.model.catalog.WireGauge;
import com.endel.demobox.model.dto.WiringSettingsDto;
import com.endel.demobox.model.dto.catalog.compatibility.CompatibilitySeverity;
import com.endel.demobox.model.dto.catalog.compatibility.ExpansionLevel;
import com.endel.demobox.model.dto.catalog.compatibility.KitCompatibilityReportDto;
import com.endel.demobox.model.dto.catalog.compatibility.WiringSegmentDto;
import com.endel.demobox.repository.catalog.ConnectionCableRepository;
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
    private long nextId = 1;

    @BeforeEach
    void setUp() {
        cableRepository = mock(ConnectionCableRepository.class);
        when(cableRepository.findAll()).thenReturn(List.of());
        service = new KitCompatibilityService(cableRepository);
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

    @Test
    void oneKilowattInverterOnTwelveVolts_needsTwoOughtCableAndA125AmpFuse() {
        // 1000 W / (12 V x 0.9) = 92.6 A operating; x1.25 = 115.7 A -> 1/0 (100 A) is too small, 2/0 (150 A) fits.
        Inverter inverter = inverter(1.0, 12.0, InverterType.OFF_GRID);
        KitCompatibilityReportDto report = service.analyze(List.of(
                new KitLine(inverter, 1),
                new KitLine(battery(12.8, 100), 1)), lengths);

        WiringSegmentDto batteryRun = segment(report, "Batería");
        assertEquals(92.6, batteryRun.getOperatingCurrentA(), 0.1);
        assertEquals(WireGauge.AWG_2_0, batteryRun.getGauge());
        assertEquals(125, batteryRun.getFuseA());
        assertTrue(batteryRun.getVoltageDropPercent() < 3.0);
        assertTrue(report.getBatteryBank().startsWith("1 en serie × 1 en paralelo"));
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
        assertEquals(WireGauge.AWG_10, segment(report, "Paneles").getGauge()); // 13.9 A x 1.25 = 17.4 A
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
}
