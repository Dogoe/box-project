package com.endel.demobox.service.catalog;

import com.endel.demobox.model.catalog.Battery;
import com.endel.demobox.model.catalog.BatteryChemistry;
import com.endel.demobox.model.catalog.ConnectionCable;
import com.endel.demobox.model.catalog.Inverter;
import com.endel.demobox.model.catalog.InverterType;
import com.endel.demobox.model.catalog.SolarPanel;
import com.endel.demobox.model.catalog.WireGauge;
import com.endel.demobox.model.dto.WiringSettingsDto;
import com.endel.demobox.model.dto.catalog.compatibility.CompatibilitySeverity;
import com.endel.demobox.model.dto.catalog.compatibility.KitCompatibilityReportDto;
import com.endel.demobox.model.dto.catalog.compatibility.WiringSegmentDto;
import com.endel.demobox.repository.catalog.ConnectionCableRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
