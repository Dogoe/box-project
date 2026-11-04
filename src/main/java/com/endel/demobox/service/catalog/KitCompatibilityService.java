package com.endel.demobox.service.catalog;

import com.endel.demobox.model.catalog.Battery;
import com.endel.demobox.model.catalog.ConnectionCable;
import com.endel.demobox.model.catalog.Inverter;
import com.endel.demobox.model.catalog.InverterType;
import com.endel.demobox.model.catalog.Product;
import com.endel.demobox.model.catalog.SolarPanel;
import com.endel.demobox.model.catalog.WireGauge;
import com.endel.demobox.model.dto.WiringSettingsDto;
import com.endel.demobox.model.dto.catalog.compatibility.KitCompatibilityReportDto;
import com.endel.demobox.model.dto.catalog.compatibility.WiringSegmentDto;
import com.endel.demobox.repository.catalog.ConnectionCableRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Checks that a kit's inverter (with its built-in MPPT charger), batteries and panels work
 * together, works out how to wire the battery bank and panel strings, and sizes the
 * battery-to-inverter and panels-to-inverter cables (gauge, voltage drop, fuse).
 * <p>
 * Everything here is derived from product specs at request time - nothing is stored.
 * Missing spec data produces a warning instead of failing, so half-filled catalogs still work.
 */
@Service
public class KitCompatibilityService {

    // Panel Voc rises in cold weather; size strings so even a cold-morning Voc stays under the MPPT max.
    static final double PV_COLD_VOC_FACTOR = 1.25;
    // Typical Vmp/Voc ratio for crystalline panels; used for the MPPT minimum-voltage check.
    static final double PV_VMP_TO_VOC_RATIO = 0.80;
    // Continuous-load safety factor applied before choosing wire and fuse.
    static final double CURRENT_SAFETY_FACTOR = 1.25;
    // Inverters draw more DC than their AC rating because of conversion losses.
    static final double INVERTER_DC_EFFICIENCY = 0.90;
    static final double MAX_VOLTAGE_DROP_PERCENT = 3.0;
    // Battery nominal voltages don't match bus voltages exactly (e.g. 51.2 V LiFePO4 on a 48 V inverter).
    static final double NOMINAL_VOLTAGE_TOLERANCE = 0.10;
    // Beyond this ratio of array watts to MPPT charge power, a meaningful share of production is clipped.
    static final double OVERPANELING_LIMIT = 1.30;
    static final double HIGH_CURRENT_WARNING_A = 150;
    // PV strings need their own fuse once 3+ are paralleled on one input (Isc x 1.56).
    static final int MIN_PARALLEL_STRINGS_NEEDING_FUSE = 3;
    static final double PV_STRING_FUSE_FACTOR = 1.56;
    static final int[] STANDARD_FUSES_A = {10, 15, 20, 25, 30, 40, 50, 60, 70, 80, 100, 125, 150, 175, 200, 225, 250, 300, 350, 400, 500};

    private final ConnectionCableRepository cableRepository;

    public KitCompatibilityService(ConnectionCableRepository cableRepository) {
        this.cableRepository = cableRepository;
    }

    public KitCompatibilityReportDto analyze(List<KitLine> lines, WiringSettingsDto lengths) {
        KitCompatibilityReportDto report = new KitCompatibilityReportDto();
        List<KitLine> panels = ofType(lines, SolarPanel.class);
        List<KitLine> batteries = ofType(lines, Battery.class);
        List<KitLine> inverters = ofType(lines, Inverter.class);
        List<KitLine> cables = ofType(lines, ConnectionCable.class);

        if (inverters.isEmpty()) {
            if (!panels.isEmpty() || !batteries.isEmpty()) {
                report.warn("El kit no incluye inversor; no se puede validar la compatibilidad ni calcular el cableado.");
            }
            return report;
        }

        Inverter inverter = (Inverter) inverters.get(0).product();
        int inverterCount = inverters.get(0).quantity();
        if (distinctProducts(inverters) > 1) {
            report.warn("El kit mezcla modelos de inversor; se valida solo con " + inverter.getName() + ".");
        }

        BatteryBank bank = analyzeBatteryBank(report, batteries, inverter);
        PvArray pv = analyzePanels(report, panels, inverter, inverterCount);

        List<WiringSegmentDto> segments = new ArrayList<>();
        if (bank != null) {
            segments.add(batteryToInverterSegment(report, inverter, inverterCount, bank, lengths.getBatteryToInverterMeters()));
        }
        if (pv != null) {
            segments.add(pvToInverterSegment(report, pv, lengths.getPvToInverterMeters()));
        }
        report.setWiring(segments);
        checkKitCables(report, cables, segments);

        return report;
    }

    // ---- Battery bank ----

    private record BatteryBank(int series, int parallel, double maxDischargeA) {
    }

    private BatteryBank analyzeBatteryBank(KitCompatibilityReportDto report, List<KitLine> batteries, Inverter inverter) {
        if (batteries.isEmpty()) {
            if (inverter.getInverterType() == InverterType.OFF_GRID) {
                report.error("El inversor off-grid necesita baterías y el kit no incluye ninguna.");
            } else if (inverter.getInverterType() == InverterType.HYBRID) {
                report.warn("El kit no incluye baterías; el inversor híbrido no tendrá respaldo de noche.");
            }
            return null;
        }
        if (inverter.getInverterType() == InverterType.STRING || inverter.getInverterType() == InverterType.MICRO) {
            report.error("El inversor " + inverter.getName() + " es de conexión a red (" + inverter.getInverterType()
                    + ") y no admite baterías.");
            return null;
        }

        Battery battery = (Battery) batteries.get(0).product();
        if (distinctProducts(batteries) > 1) {
            boolean sameVoltage = batteries.stream().allMatch(l -> ((Battery) l.product()).getVoltage() == battery.getVoltage());
            if (!sameVoltage) {
                report.error("El kit mezcla baterías de distinto voltaje; no se pueden conectar en un mismo banco.");
                return null;
            }
            report.warn("El kit mezcla modelos de batería; se recomienda un solo modelo por banco.");
        }
        if (inverter.getBatteryVoltage() == null) {
            report.warn("Falta capturar el voltaje de baterías del inversor " + inverter.getName()
                    + "; no se puede validar el banco ni calcular su cable.");
            return null;
        }
        if (battery.getCapacityAh() == null) {
            report.warn("Falta capturar la capacidad (Ah) de la batería " + battery.getName() + ".");
            return null;
        }

        double inverterVolts = inverter.getBatteryVoltage();
        double batteryVolts = battery.getVoltage();
        int count = totalQuantity(batteries);
        int series = (int) Math.round(inverterVolts / batteryVolts);
        if (series < 1 || Math.abs(series * batteryVolts - inverterVolts) / inverterVolts > NOMINAL_VOLTAGE_TOLERANCE) {
            report.error(String.format(Locale.US, "Las baterías de %s V no se pueden combinar para el inversor de %s V.",
                    fmt(batteryVolts), fmt(inverterVolts)));
            return null;
        }
        if (count % series != 0) {
            report.error(String.format(Locale.US,
                    "Para %s V con baterías de %s V se conectan %d en serie, así que se necesitan múltiplos de %d; el kit trae %d.",
                    fmt(inverterVolts), fmt(batteryVolts), series, series, count));
            return null;
        }

        int parallel = count / series;
        report.setBatteryBank(String.format(Locale.US, "%d en serie × %d en paralelo · %s V, %s Ah (%s kWh)",
                series, parallel, fmt(series * batteryVolts), fmt(parallel * battery.getCapacityAh()),
                fmt(count * battery.getCapacityKwh())));
        report.ok(String.format(Locale.US, "Banco de baterías compatible con el inversor de %s V (%dS%dP).",
                fmt(inverterVolts), series, parallel));
        return new BatteryBank(series, parallel, parallel * battery.getMaxDischargeCurrentA());
    }

    // ---- Panel strings ----

    private record PvArray(int series, int strings, int stringsPerChannel, double panelIscA, double stringVmp) {
    }

    private PvArray analyzePanels(KitCompatibilityReportDto report, List<KitLine> panels, Inverter inverter, int inverterCount) {
        if (panels.isEmpty()) {
            return null;
        }
        SolarPanel panel = (SolarPanel) panels.get(0).product();
        if (distinctProducts(panels) > 1) {
            report.warn("El kit mezcla modelos de panel; cada cadena debe llevar un solo modelo. Se valida con " + panel.getName() + ".");
        }
        if (panel.getVoc() <= 0 || panel.getIsc() <= 0) {
            report.warn("Falta capturar Voc/Isc del panel " + panel.getName() + "; no se puede validar el acomodo de paneles.");
            return null;
        }
        if (inverter.getMaxInputVoltage() <= 0) {
            report.warn("Falta capturar el voltaje PV máximo del MPPT del inversor " + inverter.getName() + ".");
            return null;
        }

        int count = totalQuantity(panels);
        double coldVoc = panel.getVoc() * PV_COLD_VOC_FACTOR;
        double vmp = panel.getVoc() * PV_VMP_TO_VOC_RATIO;
        int maxSeries = (int) Math.floor(inverter.getMaxInputVoltage() / coldVoc);
        if (maxSeries < 1) {
            report.error(String.format(Locale.US, "El Voc del panel (%s V, ~%s V en frío) supera el máximo del MPPT (%s V).",
                    fmt(panel.getVoc()), fmt(coldVoc), fmt(inverter.getMaxInputVoltage())));
            return null;
        }
        int minSeries = inverter.getMpptMinVoltage() == null ? 1 : Math.max(1, (int) Math.ceil(inverter.getMpptMinVoltage() / vmp));
        if (minSeries > maxSeries) {
            report.error(String.format(Locale.US, "Ningún acomodo en serie de este panel cae dentro del rango del MPPT (%s–%s V).",
                    fmt(inverter.getMpptMinVoltage()), fmt(inverter.getMaxInputVoltage())));
            return null;
        }
        if (count < minSeries) {
            report.error(String.format(Locale.US,
                    "Se necesitan al menos %d paneles en serie para alcanzar el voltaje mínimo del MPPT (%s V); el kit trae %d.",
                    minSeries, fmt(inverter.getMpptMinVoltage()), count));
            return null;
        }

        // Longest string that splits the panels evenly: fewer strings in parallel means less current to wire.
        int series = 0;
        for (int s = Math.min(maxSeries, count); s >= minSeries; s--) {
            if (count % s == 0) {
                series = s;
                break;
            }
        }
        boolean evenSplit = series > 0;
        if (!evenSplit) {
            series = Math.min(maxSeries, count);
            report.warn(String.format(Locale.US,
                    "%d paneles no se reparten en cadenas iguales de %d a %d paneles en serie; ajusta la cantidad.",
                    count, minSeries, maxSeries));
        }

        int strings = (int) Math.ceil((double) count / series);
        int channels = Math.max(1, inverter.getMpptChannels() == null ? 1 : inverter.getMpptChannels()) * inverterCount;
        int stringsPerChannel = (int) Math.ceil((double) strings / channels);
        double channelIsc = panel.getIsc() * stringsPerChannel;

        if (inverter.getMpptMaxInputCurrentA() == null) {
            report.warn("Falta la corriente PV máxima por canal del inversor " + inverter.getName()
                    + "; no se validó la corriente de los paneles.");
        } else if (channelIsc > inverter.getMpptMaxInputCurrentA()) {
            report.warn(String.format(Locale.US,
                    "Cada entrada MPPT recibiría %s A (Isc) y su máximo es %s A; usa menos cadenas en paralelo por entrada.",
                    fmt(channelIsc), fmt(inverter.getMpptMaxInputCurrentA())));
        }

        double arrayWatts = count * panel.getWattage();
        double maxChargeWatts = inverter.getMaxChargePowerW() * inverterCount;
        if (maxChargeWatts > 0 && arrayWatts > maxChargeWatts * OVERPANELING_LIMIT) {
            report.warn(String.format(Locale.US,
                    "El arreglo de %s W supera por mucho la potencia de carga del MPPT (%s W); parte de la producción se desperdiciará.",
                    fmt(arrayWatts), fmt(maxChargeWatts)));
        }

        report.setPanelArray(String.format(Locale.US, "%d %s de %d paneles en serie · ~%s V de operación, %s V Voc en frío",
                strings, strings == 1 ? "cadena" : "cadenas", series, fmt(series * vmp), fmt(series * coldVoc)));
        if (evenSplit) {
            report.ok(String.format(Locale.US, "Acomodo de paneles dentro del rango del MPPT (máx. %s V).",
                    fmt(inverter.getMaxInputVoltage())));
        }
        return new PvArray(series, strings, stringsPerChannel, panel.getIsc(), series * vmp);
    }

    // ---- Wiring ----

    private WiringSegmentDto batteryToInverterSegment(KitCompatibilityReportDto report, Inverter inverter, int inverterCount,
                                                      BatteryBank bank, double lengthMeters) {
        double busVolts = inverter.getBatteryVoltage();
        double dischargeA = inverter.getRatedPowerKw() * 1000 / (busVolts * INVERTER_DC_EFFICIENCY);
        double chargeA = inverter.getMpptMaxChargeCurrentA() == null ? 0 : inverter.getMpptMaxChargeCurrentA();
        if (inverter.getMpptMaxChargeCurrentA() == null) {
            report.warn("Falta la corriente de carga del MPPT del inversor " + inverter.getName()
                    + "; el cable de batería se calculó solo con la descarga.");
        }
        double operatingA = Math.max(dischargeA, chargeA);

        WiringSegmentDto segment = sizeSegment(report, "Batería – inversor", operatingA, busVolts, lengthMeters);
        segment.setRuns(inverterCount);
        segment.setFuseNote("Fusible o breaker DC entre batería e inversor");

        if (operatingA > HIGH_CURRENT_WARNING_A && busVolts < 48) {
            report.warn(String.format(Locale.US,
                    "La corriente batería–inversor llega a %s A en %s V; considera un banco de 24 V o 48 V.",
                    fmt(operatingA), fmt(busVolts)));
        }
        double totalDischargeA = dischargeA * inverterCount;
        if (bank.maxDischargeA() > 0 && totalDischargeA > bank.maxDischargeA()) {
            report.warn(String.format(Locale.US,
                    "El banco entrega máx. ~%s A continuos y el inversor puede pedir %s A; agrega baterías en paralelo o usa un inversor menor.",
                    fmt(bank.maxDischargeA()), fmt(totalDischargeA)));
        }
        return segment;
    }

    private WiringSegmentDto pvToInverterSegment(KitCompatibilityReportDto report, PvArray pv, double lengthMeters) {
        double operatingA = pv.panelIscA() * pv.stringsPerChannel();
        WiringSegmentDto segment = sizeSegment(report, "Paneles – inversor (MPPT)", operatingA, pv.stringVmp(), lengthMeters);
        if (pv.stringsPerChannel() >= MIN_PARALLEL_STRINGS_NEEDING_FUSE) {
            segment.setFuseA(standardFuse(pv.panelIscA() * PV_STRING_FUSE_FACTOR));
            segment.setFuseNote("Fusible por cadena (3 o más cadenas en paralelo)");
        } else {
            segment.setFuseA(null);
            segment.setFuseNote(null);
        }
        return segment;
    }

    private WiringSegmentDto sizeSegment(KitCompatibilityReportDto report, String name, double operatingA, double volts, double lengthMeters) {
        double designA = operatingA * CURRENT_SAFETY_FACTOR;
        WiringSegmentDto segment = new WiringSegmentDto();
        segment.setSegment(name);
        segment.setOperatingCurrentA(round1(operatingA));
        segment.setDesignCurrentA(round1(designA));
        segment.setLengthMeters(lengthMeters);

        for (WireGauge gauge : WireGauge.values()) {
            double drop = voltageDropPercent(gauge, operatingA, volts, lengthMeters);
            if (gauge.getAmpacityA() >= designA && drop <= MAX_VOLTAGE_DROP_PERCENT) {
                segment.setGauge(gauge);
                segment.setGaugeLabel(gauge.getLabel());
                segment.setVoltageDropPercent(round1(drop));
                break;
            }
        }
        if (segment.getGauge() == null) {
            report.error(String.format(Locale.US,
                    "%s: %s A a %s m superan lo que maneja un solo cable 4/0 AWG; usa un banco de mayor voltaje o cables en paralelo.",
                    name, fmt(operatingA), fmt(lengthMeters)));
        }
        segment.setFuseA(standardFuse(designA));
        return segment;
    }

    static double voltageDropPercent(WireGauge gauge, double currentA, double volts, double oneWayMeters) {
        return 2 * oneWayMeters * currentA * gauge.getOhmsPerMeter() / volts * 100;
    }

    static Integer standardFuse(double designA) {
        for (int fuse : STANDARD_FUSES_A) {
            if (fuse >= designA) {
                return fuse;
            }
        }
        return null;
    }

    // ---- Kit's own cables ----

    private static final class CablePool {
        final ConnectionCable cable;
        double remainingMeters;

        CablePool(ConnectionCable cable, double meters) {
            this.cable = cable;
            this.remainingMeters = meters;
        }
    }

    private void checkKitCables(KitCompatibilityReportDto report, List<KitLine> cableLines, List<WiringSegmentDto> segments) {
        List<CablePool> pools = new ArrayList<>();
        for (KitLine line : cableLines) {
            ConnectionCable cable = (ConnectionCable) line.product();
            if (cable.getGauge() == null) {
                report.warn("Falta capturar el calibre del cable " + cable.getName() + "; no se tomó en cuenta.");
                continue;
            }
            pools.add(new CablePool(cable, cable.getLengthMeters() * line.quantity()));
        }

        // Thickest requirement first, so thick cable isn't spent on a run a thinner one could cover.
        List<WiringSegmentDto> sized = segments.stream()
                .filter(s -> s.getGauge() != null)
                .sorted(Comparator.comparing(WiringSegmentDto::getGauge).reversed())
                .toList();

        for (WiringSegmentDto segment : sized) {
            double needed = segment.getRequiredCableMeters();
            double covered = 0;
            List<String> usedNames = new ArrayList<>();
            List<CablePool> candidates = pools.stream()
                    .filter(p -> p.remainingMeters > 0 && p.cable.getGauge().isAtLeast(segment.getGauge()))
                    .sorted(Comparator.comparing(p -> p.cable.getGauge()))
                    .toList();
            for (CablePool pool : candidates) {
                if (covered >= needed) {
                    break;
                }
                double take = Math.min(pool.remainingMeters, needed - covered);
                pool.remainingMeters -= take;
                covered += take;
                usedNames.add(pool.cable.getName());
            }

            if (covered + 1e-6 >= needed) {
                segment.setCoveredBy(String.join(", ", usedNames));
                report.ok(String.format(Locale.US, "%s: el kit incluye cable suficiente (%s m de %s o más grueso).",
                        segment.getSegment(), fmt(needed), segment.getGaugeLabel()));
            } else {
                report.warn(String.format(Locale.US,
                        "%s: se necesitan %s m de cable %s o más grueso (positivo y negativo) y el kit cubre %s m.%s",
                        segment.getSegment(), fmt(needed), segment.getGaugeLabel(), fmt(covered),
                        suggestCable(segment.getGauge()).map(s -> " Sugerencia: " + s + ".").orElse("")));
            }
        }
    }

    private Optional<String> suggestCable(WireGauge required) {
        return cableRepository.findAll().stream()
                .filter(c -> c.isActive() && c.getGauge() != null && c.getGauge().isAtLeast(required))
                .min(Comparator.comparing(ConnectionCable::getGauge).thenComparing(ConnectionCable::getPrice))
                .map(c -> c.getSku() + " – " + c.getName() + " (" + c.getGauge().getLabel() + ", " + fmt(c.getLengthMeters()) + " m)");
    }

    // ---- Helpers ----

    private static List<KitLine> ofType(List<KitLine> lines, Class<? extends Product> type) {
        return lines.stream().filter(l -> type.isInstance(l.product())).toList();
    }

    private static long distinctProducts(List<KitLine> lines) {
        return lines.stream().map(l -> l.product().getId() != null ? l.product().getId() : (Object) l.product()).distinct().count();
    }

    private static int totalQuantity(List<KitLine> lines) {
        return lines.stream().mapToInt(KitLine::quantity).sum();
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private static String fmt(Double value) {
        if (value == null) {
            return "-";
        }
        double rounded = round1(value);
        return rounded == Math.rint(rounded) ? String.format(Locale.US, "%.0f", rounded) : String.format(Locale.US, "%.1f", rounded);
    }
}
