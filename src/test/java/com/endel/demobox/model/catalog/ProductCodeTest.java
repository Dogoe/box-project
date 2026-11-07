package com.endel.demobox.model.catalog;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ProductCodeTest {

    private static <T extends Product> T priced(T product, String price) {
        product.setPrice(new BigDecimal(price));
        return product;
    }

    @Test
    void inverterShowsPowerBatteryVoltageAcVoltageAndMppt() {
        Inverter inverter = priced(new Inverter(), "3000.00");
        inverter.setRatedPowerKw(1.0);
        inverter.setBatteryVoltage(12.0);
        inverter.setBuiltInCharger(false);
        assertThat(inverter.getCode()).isEqualTo("INVERSOR-1000W-12V-120V-$3000");

        inverter.setBuiltInCharger(true);
        inverter.setRatedPowerKw(3.5);
        inverter.setBatteryVoltage(48.0);
        assertThat(inverter.getCode()).isEqualTo("INVERSOR-3500W-48V-120V-MPPT-$3000");
    }

    @Test
    void inverterWithoutBatteriesSkipsTheDcVoltage() {
        Inverter inverter = priced(new Inverter(), "9800");
        inverter.setRatedPowerKw(5.0);
        inverter.setBuiltInCharger(false);
        assertThat(inverter.getCode()).isEqualTo("INVERSOR-5000W-120V-$9800");
    }

    @Test
    void smallInverterPowerHasNoFloatingPointNoise() {
        Inverter inverter = priced(new Inverter(), "1500");
        inverter.setRatedPowerKw(0.6);
        inverter.setBatteryVoltage(12.0);
        inverter.setBuiltInCharger(false);
        assertThat(inverter.getCode()).isEqualTo("INVERSOR-600W-12V-120V-$1500");
    }

    @Test
    void controllerShowsChargeCurrentBankVoltagesAndPvLimit() {
        ChargeController controller = priced(new ChargeController(), "2300");
        controller.setMaxChargeCurrentA(60);
        controller.setMaxInputVoltage(150);
        controller.getSupportedBatteryVoltages().addAll(java.util.Set.of(24, 12));
        assertThat(controller.getCode()).isEqualTo("CONTROLADOR-60A-12/24V-PV150V-$2300");
    }

    @Test
    void controllerVoltagesAreSortedEvenWhenTheSetIsNot() {
        // Hibernate loads the collection as a hash set, so the order can't be trusted.
        ChargeController controller = priced(new ChargeController(), "2300");
        controller.setMaxChargeCurrentA(60);
        controller.setMaxInputVoltage(150);
        controller.setSupportedBatteryVoltages(new java.util.LinkedHashSet<>(java.util.List.of(48, 12, 24)));
        assertThat(controller.getCode()).isEqualTo("CONTROLADOR-60A-12/24/48V-PV150V-$2300");
    }

    @Test
    void batteryShowsChemistryVoltageAndCapacity() {
        Battery battery = priced(new Battery(), "8900");
        battery.setChemistry(BatteryChemistry.LIFEPO4);
        battery.setVoltage(12.8);
        battery.setCapacityAh(100.0);
        assertThat(battery.getCode()).isEqualTo("BATERIA-LFP-12.8V-100Ah-$8900");

        battery.setChemistry(BatteryChemistry.AGM);
        battery.setVoltage(12);
        battery.setCapacityAh(null);
        assertThat(battery.getCode()).isEqualTo("BATERIA-AGM-12V-$8900");
    }

    @Test
    void panelShowsWattsVocAndConnector() {
        SolarPanel panel = priced(new SolarPanel(), "2450");
        panel.setWattage(550);
        panel.setVoc(49.5);
        assertThat(panel.getCode()).isEqualTo("PANEL-550W-49.5Voc-MC4-$2450");
        panel.setConnector(PanelConnector.SAE);
        assertThat(panel.getCode()).isEqualTo("PANEL-550W-49.5Voc-SAE-$2450");
    }

    private static Protection protection(ProtectionType type, FuseClass fuseClass, double amps, double volts, Integer poles, String price) {
        Protection protection = priced(new Protection(), price);
        protection.setProtectionType(type);
        protection.setFuseClass(fuseClass);
        protection.setRatedCurrentA(amps);
        protection.setMaxVoltage(volts);
        protection.setPoles(poles);
        return protection;
    }

    @Test
    void protectionsShowTheirKindClassCurrentAndVoltage() {
        assertThat(protection(ProtectionType.FUSE, FuseClass.CLASS_T, 150, 150, 1, "850").getCode()).isEqualTo("FUSIBLE-CLASE-T-150A-150V-$850");
        assertThat(protection(ProtectionType.FUSE, FuseClass.ANL, 80, 32, 1, "150").getCode()).isEqualTo("FUSIBLE-ANL-80A-32V-$150");
        assertThat(protection(ProtectionType.FUSE, FuseClass.OTHER, 15, 32, 1, "60").getCode()).isEqualTo("FUSIBLE-OTRO-15A-32V-$60");
        assertThat(protection(ProtectionType.DC_BREAKER, null, 250, 24, 1, "450").getCode()).isEqualTo("BREAKER-DC-250A-24V-$450");
        assertThat(protection(ProtectionType.PV_DISCONNECT, null, 32, 1000, 2, "280").getCode()).isEqualTo("DESCONECTADOR-PV-2P-32A-1000V-$280");
        assertThat(protection(ProtectionType.AC_BREAKER, null, 15, 120, 1, "420").getCode()).isEqualTo("BREAKER-AC-15A-1P-120V-$420");
    }

    @Test
    void aMultiPoleDcBreakerShowsItsPoles() {
        assertThat(protection(ProtectionType.DC_BREAKER, null, 63, 550, 2, "700").getCode()).isEqualTo("BREAKER-DC-63A-550V-2P-$700");
    }

    @Test
    void panelWithoutVocSkipsIt() {
        SolarPanel panel = priced(new SolarPanel(), "400");
        panel.setWattage(400);
        assertThat(panel.getCode()).isEqualTo("PANEL-400W-MC4-$400");
    }

    @Test
    void cableAndTerminalShowGaugeAndSize() {
        ConnectionCable cable = priced(new ConnectionCable(), "650");
        cable.setGauge(WireGauge.AWG_4);
        cable.setLengthMeters(10);
        assertThat(cable.getCode()).isEqualTo("CABLE-4AWG-10M-$650");
        cable.setGauge(WireGauge.AWG_1_0);
        cable.setLengthMeters(2.5);
        assertThat(cable.getCode()).isEqualTo("CABLE-1/0AWG-2.5M-$650");

        Terminal terminal = priced(new Terminal(), "80");
        terminal.setGauge(WireGauge.AWG_4);
        terminal.setTerminalSize(TerminalSize.M8);
        terminal.setPiecesPerPack(10);
        assertThat(terminal.getCode()).isEqualTo("TERMINAL-4AWG-M8-x10-$80");
    }

    @Test
    void structureUsesItsSkuBecauseItHasNoSpecs() {
        AluminumStructure structure = priced(new AluminumStructure(), "1200");
        structure.setSku("EST-RIEL-2P");
        assertThat(structure.getCode()).isEqualTo("ESTRUCTURA-EST-RIEL-2P-$1200");
    }

    @Test
    void priceKeepsCentsOnlyWhenThereAreAnyAndNeverUsesSeparators() {
        Battery battery = priced(new Battery(), "3000.50");
        battery.setVoltage(12);
        battery.setChemistry(BatteryChemistry.GEL);
        assertThat(battery.getCode()).endsWith("-$3000.5");
        battery.setPrice(new BigDecimal("12345.00"));
        assertThat(battery.getCode()).endsWith("-$12345");
        battery.setPrice(new BigDecimal("0"));
        assertThat(battery.getCode()).endsWith("-$0");
        battery.setPrice(null);
        assertThat(battery.getCode()).endsWith("-$0");
    }

    @Test
    void theCodeFollowsTheCurrentPrice() {
        Battery battery = priced(new Battery(), "8900");
        battery.setVoltage(12.8);
        battery.setChemistry(BatteryChemistry.LIFEPO4);
        String before = battery.getCode();
        battery.setPrice(new BigDecimal("9100"));
        assertThat(battery.getCode()).isNotEqualTo(before).endsWith("-$9100");
    }
}
