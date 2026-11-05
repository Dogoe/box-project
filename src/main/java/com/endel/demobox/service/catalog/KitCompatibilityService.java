package com.endel.demobox.service.catalog;

import com.endel.demobox.model.catalog.Battery;
import com.endel.demobox.model.catalog.ChargeController;
import com.endel.demobox.model.catalog.ConnectionCable;
import com.endel.demobox.model.catalog.Inverter;
import com.endel.demobox.model.catalog.InverterType;
import com.endel.demobox.model.catalog.Product;
import com.endel.demobox.model.catalog.SolarPanel;
import com.endel.demobox.model.catalog.WireGauge;
import com.endel.demobox.model.dto.WiringSettingsDto;
import com.endel.demobox.model.dto.catalog.compatibility.CompatibilitySeverity;
import com.endel.demobox.model.dto.catalog.compatibility.ExpansionAspectDto;
import com.endel.demobox.model.dto.catalog.compatibility.ExpansionLevel;
import com.endel.demobox.model.dto.catalog.compatibility.KitCompatibilityReportDto;
import com.endel.demobox.model.dto.catalog.compatibility.KitExpansionDto;
import com.endel.demobox.model.dto.catalog.compatibility.WiringSegmentDto;
import com.endel.demobox.repository.catalog.ConnectionCableRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Checks that a kit's inverter, solar charger, batteries and panels work together, works out how
 * to wire the battery bank and panel strings, and sizes the cables (gauge, voltage drop, fuse).
 * <p>
 * The solar charger is the inverter's built-in MPPT when it has one; otherwise the kit's separate
 * charge controller, which then gets its own panels and battery cable runs.
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
    // No kit sold so far comes close; anything above this is most likely a unit typo (W entered as kW).
    // Raise it when real systems outgrow it.
    static final double SANITY_LIMIT = 30;
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
        List<KitLine> controllers = ofType(lines, ChargeController.class);

        checkSanityLimits(report, panels, batteries, inverters);

        // A kit with mixed models isn't valid (and can't be saved), so there's nothing sound to size.
        List<String> compositionErrors = compositionErrors(lines);
        if (!compositionErrors.isEmpty()) {
            compositionErrors.forEach(report::error);
            return report;
        }

        if (inverters.isEmpty()) {
            if (!panels.isEmpty() || !batteries.isEmpty() || !controllers.isEmpty()) {
                report.warn("El kit no incluye inversor; no se puede validar la compatibilidad ni calcular el cableado.");
            }
            return report;
        }

        Inverter inverter = (Inverter) inverters.get(0).product();

        SolarCharger charger = resolveCharger(report, inverter, controllers, !panels.isEmpty());
        BatteryBank bank = analyzeBatteryBank(report, batteries, inverter);
        PvArray pv = charger == null ? null : analyzePanels(report, panels, charger);

        List<WiringSegmentDto> segments = new ArrayList<>();
        if (bank != null) {
            segments.add(batteryToInverterSegment(report, inverter, charger, bank, lengths.getBatteryToInverterMeters()));
            if (charger != null && !charger.builtIn()) {
                segments.add(controllerToBatterySegment(report, charger, lengths.getControllerToBatteryMeters()));
            }
        }
        if (pv != null) {
            segments.add(pvSegment(report, pv, charger, lengths.getPvToInverterMeters()));
        }
        report.setWiring(segments);
        checkKitCables(report, cables, segments);

        if (report.getStatus() != CompatibilitySeverity.ERROR) {
            report.setExpansion(expansion(inverter, charger, !panels.isEmpty(), !batteries.isEmpty(), pv, bank));
        }
        return report;
    }

    // ---- Solar charger ----

    /**
     * The kit's solar charger, whichever product provides it: the inverter's built-in MPPT, or a
     * separate charge controller on the inverter's battery bus. {@code owner} names the product in
     * messages, {@code noun} is just "inversor"/"controlador" and {@code kind} the same with its article.
     */
    private record SolarCharger(boolean builtIn, String owner, String noun, String kind, double maxInputVoltage, Double minVoltage,
                                int channels, Double maxInputCurrentA, Double chargeCurrentA, Double busVolts,
                                double chargePowerW, double effectiveMaxPvPowerW, boolean pvPowerEstimated) {

        static SolarCharger builtInto(Inverter inverter) {
            return new SolarCharger(true, "inversor " + inverter.getName(), "inversor", "el inversor", inverter.getMaxInputVoltage(),
                    inverter.getMpptMinVoltage(), channelsOf(inverter.getMpptChannels()), inverter.getMpptMaxInputCurrentA(),
                    inverter.getMpptMaxChargeCurrentA(), inverter.getBatteryVoltage(), inverter.getMaxChargePowerW(),
                    inverter.getEffectiveMaxPvPowerW(), inverter.isMaxPvPowerEstimated());
        }

        static SolarCharger separate(ChargeController controller, Double busVolts) {
            double volts = busVolts == null ? 0 : busVolts;
            return new SolarCharger(false, "controlador " + controller.getName(), "controlador", "el controlador", controller.getMaxInputVoltage(),
                    controller.getMpptMinVoltage(), channelsOf(controller.getMpptChannels()), controller.getMpptMaxInputCurrentA(),
                    controller.getMaxChargeCurrentA(), busVolts, controller.maxChargePowerW(volts),
                    controller.effectiveMaxPvPowerW(volts), controller.isMaxPvPowerEstimated());
        }

        private static int channelsOf(Integer channels) {
            return Math.max(1, channels == null ? 1 : channels);
        }
    }

    /**
     * Inverter with a built-in charger: that one is used (a controller in the kit is ignored, with a
     * warning). Inverter without one: the kit's controller, which must support the battery voltage.
     */
    private SolarCharger resolveCharger(KitCompatibilityReportDto report, Inverter inverter, List<KitLine> controllers, boolean hasPanels) {
        ChargeController controller = controllers.isEmpty() ? null : (ChargeController) controllers.get(0).product();
        if (inverter.hasBuiltInCharger()) {
            if (controller != null) {
                report.warn("El inversor " + inverter.getName() + " ya trae cargador solar (MPPT); se usa el suyo y el controlador "
                        + controller.getName() + " no se toma en cuenta en los cálculos.");
            }
            report.setSolarCharger("Integrado en el inversor" + (inverter.getMpptMaxChargeCurrentA() != null
                    ? " · " + fmt(inverter.getMpptMaxChargeCurrentA()) + " A de carga" : ""));
            return SolarCharger.builtInto(inverter);
        }

        if (controller == null) {
            if (hasPanels) {
                report.error("El inversor " + inverter.getName()
                        + " no tiene cargador solar; agrega un controlador de carga MPPT para conectar los paneles.");
            }
            return null;
        }
        Double busVolts = inverter.getBatteryVoltage();
        if (busVolts != null && !controller.supportsBatteryVoltage(busVolts)) {
            report.error(String.format(Locale.US, "El controlador %s no admite baterías de %s V (admite %s V).",
                    controller.getName(), fmt(busVolts),
                    controller.getSupportedBatteryVoltages().stream().sorted().map(String::valueOf).collect(Collectors.joining("/"))));
            return null;
        }
        if (!hasPanels) {
            report.warn("El kit incluye el controlador " + controller.getName() + " pero no trae paneles.");
        }
        report.setSolarCharger("Controlador " + controller.getName() + " · " + fmt(controller.getMaxChargeCurrentA()) + " A de carga");
        if (busVolts != null) {
            report.ok(String.format(Locale.US, "El controlador %s carga el banco de %s V.", controller.getName(), fmt(busVolts)));
        }
        return SolarCharger.separate(controller, busVolts);
    }

    // ---- Composition ----

    /**
     * Residential kits use one inverter (these units can't be paralleled), at most one separate charge
     * controller, and a single battery model and panel model: mixed batteries charge/discharge unevenly, and mixed panels in a
     * string are limited by the weakest one (single-MPPT inverters can't separate them). Extras like
     * cables and structures are unrestricted. Used both to block saving and in the live review.
     */
    public List<String> compositionErrors(List<KitLine> lines) {
        List<String> errors = new ArrayList<>();
        List<KitLine> inverters = ofType(lines, Inverter.class);
        if (distinctProducts(inverters) > 1) {
            errors.add("Un kit solo puede tener un inversor; este trae " + distinctProducts(inverters) + " modelos: " + names(inverters) + ".");
        } else if (totalQuantity(inverters) > 1) {
            errors.add("Un kit solo puede tener un inversor (cantidad 1); este trae " + totalQuantity(inverters) + ".");
        }
        List<KitLine> controllers = ofType(lines, ChargeController.class);
        if (distinctProducts(controllers) > 1) {
            errors.add("Un kit solo puede tener un controlador de carga; este trae " + distinctProducts(controllers) + " modelos: " + names(controllers) + ".");
        } else if (totalQuantity(controllers) > 1) {
            errors.add("Un kit solo puede tener un controlador de carga (cantidad 1); este trae " + totalQuantity(controllers) + ".");
        }
        List<KitLine> batteries = ofType(lines, Battery.class);
        if (distinctProducts(batteries) > 1) {
            errors.add("Un kit solo puede tener un modelo de batería; este trae " + distinctProducts(batteries) + ": " + names(batteries) + ".");
        }
        List<KitLine> panels = ofType(lines, SolarPanel.class);
        if (distinctProducts(panels) > 1) {
            errors.add("Un kit solo puede tener un modelo de panel; este trae " + distinctProducts(panels) + ": " + names(panels) + ".");
        }
        return errors;
    }

    private static String names(List<KitLine> lines) {
        return lines.stream().map(l -> l.product().getName()).distinct().collect(Collectors.joining(", "));
    }

    // ---- Capture sanity ----

    private void checkSanityLimits(KitCompatibilityReportDto report, List<KitLine> panels, List<KitLine> batteries, List<KitLine> inverters) {
        double batteryKwh = batteries.stream().mapToDouble(l -> ((Battery) l.product()).getCapacityKwh() * l.quantity()).sum();
        double inverterKw = inverters.stream().mapToDouble(l -> ((Inverter) l.product()).getRatedPowerKw() * l.quantity()).sum();
        double panelKwp = panels.stream().mapToDouble(l -> ((SolarPanel) l.product()).getWattage() * l.quantity() / 1000.0).sum();

        if (batteryKwh > SANITY_LIMIT) {
            report.warn(String.format(Locale.US,
                    "El banco de baterías suma %s kWh, demasiado para un kit (máx. habitual %s kWh); revisa que la capacidad (Ah) y el voltaje estén bien capturados.",
                    fmt(batteryKwh), fmt(SANITY_LIMIT)));
        }
        if (inverterKw > SANITY_LIMIT) {
            report.warn(String.format(Locale.US,
                    "Los inversores suman %s kW, demasiado para un kit (máx. habitual %s kW); revisa que la potencia esté en kW y no en W.",
                    fmt(inverterKw), fmt(SANITY_LIMIT)));
        }
        if (panelKwp > SANITY_LIMIT) {
            report.warn(String.format(Locale.US,
                    "Los paneles suman %s kWp, demasiado para un kit (máx. habitual %s kWp); revisa que la potencia de cada panel esté en W.",
                    fmt(panelKwp), fmt(SANITY_LIMIT)));
        }
    }

    // ---- Battery bank ----

    private record BatteryBank(Battery battery, int series, int parallel, double maxDischargeA) {
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
        return new BatteryBank(battery, series, parallel, parallel * battery.getMaxDischargeCurrentA());
    }

    // ---- Panel strings ----

    private record PvArray(SolarPanel panel, int count, int minSeries, int maxSeries, int channels,
                           int series, int strings, int stringsPerChannel, double panelIscA, double stringVmp) {
    }

    private PvArray analyzePanels(KitCompatibilityReportDto report, List<KitLine> panels, SolarCharger charger) {
        if (panels.isEmpty()) {
            return null;
        }
        SolarPanel panel = (SolarPanel) panels.get(0).product();
        if (panel.getVoc() <= 0 || panel.getIsc() <= 0) {
            report.warn("Falta capturar Voc/Isc del panel " + panel.getName() + "; no se puede validar el acomodo de paneles.");
            return null;
        }
        if (charger.maxInputVoltage() <= 0) {
            report.warn("Falta capturar el voltaje PV máximo del MPPT del " + charger.owner() + ".");
            return null;
        }

        int count = totalQuantity(panels);
        double coldVoc = panel.getVoc() * PV_COLD_VOC_FACTOR;
        double vmp = panel.getVoc() * PV_VMP_TO_VOC_RATIO;
        int maxSeries = (int) Math.floor(charger.maxInputVoltage() / coldVoc);
        if (maxSeries < 1) {
            report.error(String.format(Locale.US, "El Voc del panel (%s V, ~%s V en frío) supera el máximo del MPPT (%s V).",
                    fmt(panel.getVoc()), fmt(coldVoc), fmt(charger.maxInputVoltage())));
            return null;
        }
        int minSeries = charger.minVoltage() == null ? 1 : Math.max(1, (int) Math.ceil(charger.minVoltage() / vmp));
        if (minSeries > maxSeries) {
            report.error(String.format(Locale.US, "Ningún acomodo en serie de este panel cae dentro del rango del MPPT (%s–%s V).",
                    fmt(charger.minVoltage()), fmt(charger.maxInputVoltage())));
            return null;
        }
        if (count < minSeries) {
            report.error(String.format(Locale.US,
                    "Se necesitan al menos %d paneles en serie para alcanzar el voltaje mínimo del MPPT (%s V); el kit trae %d.",
                    minSeries, fmt(charger.minVoltage()), count));
            return null;
        }

        int series = longestEvenSeries(count, minSeries, maxSeries);
        boolean evenSplit = series > 0;
        if (!evenSplit) {
            series = Math.min(maxSeries, count);
            report.warn(String.format(Locale.US,
                    "%d paneles no se reparten en cadenas iguales de %d a %d paneles en serie; ajusta la cantidad.",
                    count, minSeries, maxSeries));
        }

        int strings = (int) Math.ceil((double) count / series);
        int channels = charger.channels();
        int stringsPerChannel = (int) Math.ceil((double) strings / channels);
        double channelIsc = panel.getIsc() * stringsPerChannel;

        if (charger.maxInputCurrentA() == null) {
            report.warn("Falta la corriente PV máxima por canal del " + charger.owner()
                    + "; no se validó la corriente de los paneles.");
        } else if (channelIsc > charger.maxInputCurrentA()) {
            report.warn(String.format(Locale.US,
                    "Cada entrada MPPT recibiría %s A (Isc) y su máximo es %s A; usa menos cadenas en paralelo por entrada.",
                    fmt(channelIsc), fmt(charger.maxInputCurrentA())));
        }

        double arrayWatts = count * panel.getWattage();
        double maxPvWatts = charger.effectiveMaxPvPowerW();
        if (maxPvWatts > 0 && arrayWatts > maxPvWatts) {
            report.warn(charger.pvPowerEstimated()
                    ? String.format(Locale.US,
                    "El arreglo de %s W supera la potencia PV máxima estimada del %s (~%s W: carga de %s W + 30 %%); "
                            + "parte de la producción se desperdiciará. Captura la potencia PV máx. de la ficha para usar el dato exacto.",
                    fmt(arrayWatts), charger.noun(), fmt(maxPvWatts), fmt(charger.chargePowerW()))
                    : String.format(Locale.US,
                    "El arreglo de %s W supera la potencia PV máxima del %s (%s W según ficha).",
                    fmt(arrayWatts), charger.noun(), fmt(maxPvWatts)));
        }

        report.setPanelArray(String.format(Locale.US, "%d %s de %d %s en serie · ~%s V de operación, %s V Voc en frío",
                strings, strings == 1 ? "cadena" : "cadenas", series, series == 1 ? "panel" : "paneles", fmt(series * vmp), fmt(series * coldVoc)));
        if (evenSplit) {
            report.ok(String.format(Locale.US, "Acomodo de paneles dentro del rango del MPPT (máx. %s V).",
                    fmt(charger.maxInputVoltage())));
        }
        return new PvArray(panel, count, minSeries, maxSeries, channels, series, strings, stringsPerChannel, panel.getIsc(), series * vmp);
    }

    /**
     * Longest string length within [minSeries, maxSeries] that splits the panels into equal strings
     * (fewer strings in parallel means less current to wire), or 0 when none does.
     */
    private static int longestEvenSeries(int count, int minSeries, int maxSeries) {
        for (int s = Math.min(maxSeries, count); s >= minSeries; s--) {
            if (count % s == 0) {
                return s;
            }
        }
        return 0;
    }

    // ---- Expansion ----

    private KitExpansionDto expansion(Inverter inverter, SolarCharger charger, boolean hasPanels, boolean hasBatteries, PvArray pv, BatteryBank bank) {
        ExpansionAspectDto panels = hasPanels && charger != null ? panelExpansion(charger, pv) : null;
        ExpansionAspectDto batteries = hasBatteries && charger != null ? batteryExpansion(charger, bank) : null;
        // A half-known answer would mislead the customer, so only report when every part present can be worked out.
        if ((hasPanels && panels == null) || (hasBatteries && batteries == null) || (panels == null && batteries == null)) {
            return null;
        }

        List<ExpansionLevel> levels = new ArrayList<>();
        if (panels != null) {
            levels.add(panels.getLevel());
        }
        if (batteries != null) {
            levels.add(batteries.getLevel());
        }
        ExpansionLevel overall = levels.stream().allMatch(l -> l == ExpansionLevel.EXPANDABLE) ? ExpansionLevel.EXPANDABLE
                : levels.stream().allMatch(l -> l == ExpansionLevel.MAXED) ? ExpansionLevel.MAXED
                : ExpansionLevel.LIMITED;

        KitExpansionDto dto = new KitExpansionDto();
        dto.setLevel(overall);
        dto.setLabel(overall.getOverallLabel());
        dto.setPanels(panels);
        dto.setBatteries(batteries);
        dto.setInverterKw(inverter.getRatedPowerKw());
        return dto;
    }

    /** More panels of the same model, up to the charger's PV limit, as long as they still form valid equal strings. */
    private ExpansionAspectDto panelExpansion(SolarCharger charger, PvArray pv) {
        double maxPvWatts = charger.effectiveMaxPvPowerW();
        if (pv == null || maxPvWatts <= 0 || pv.panel().getWattage() <= 0) {
            return null;
        }
        int ceiling = (int) Math.floor(maxPvWatts / pv.panel().getWattage());
        int best = pv.count();
        for (int n = pv.count() + 1; n <= ceiling; n++) {
            int series = longestEvenSeries(n, pv.minSeries(), pv.maxSeries());
            if (series == 0) {
                continue;
            }
            int stringsPerChannel = (int) Math.ceil((double) (n / series) / pv.channels());
            if (charger.maxInputCurrentA() != null && pv.panelIscA() * stringsPerChannel > charger.maxInputCurrentA()) {
                continue;
            }
            best = n;
        }

        int additional = best - pv.count();
        String limit = fmt(maxPvWatts) + " W" + (charger.pvPowerEstimated() ? " estimado" : "");
        return aspect(pv.count(), additional, additional > 0
                ? String.format(Locale.US, "Hasta %d %s más (total %d; límite PV %s).", additional, additional == 1 ? "panel" : "paneles", best, limit)
                : String.format(Locale.US, "Sin espacio para más paneles (límite PV %s).", limit));
    }

    /**
     * More battery strings in parallel, up to what the solar charger can recharge in a day with panels
     * at its PV/charge limit (same sun hours and derate as the "qué puede alimentar" estimate). Beyond
     * that, extra capacity would never be refilled.
     */
    private ExpansionAspectDto batteryExpansion(SolarCharger charger, BatteryBank bank) {
        double chargeWatts = charger.chargePowerW();
        if (bank == null || chargeWatts <= 0) {
            return null;
        }
        double dailyRechargeKwh = Math.min(charger.effectiveMaxPvPowerW(), chargeWatts)
                * KitCapacityEstimator.DEFAULT_PEAK_SUN_HOURS * KitCapacityEstimator.DEFAULT_SYSTEM_DERATE_FACTOR / 1000;
        Battery battery = bank.battery();
        double usableKwhPerString = bank.series() * battery.getCapacityKwh() * battery.getDepthOfDischarge();
        if (usableKwhPerString <= 0) {
            return null;
        }
        int maxStrings = (int) Math.floor(dailyRechargeKwh / usableKwhPerString + 1e-9);
        int additionalStrings = Math.max(0, maxStrings - bank.parallel());
        int additionalBatteries = additionalStrings * bank.series();

        return aspect(bank.parallel(), additionalStrings, additionalStrings > 0
                ? String.format(Locale.US, "Hasta %d %s más en paralelo (se agregan de %d en %d; %s recarga hasta ~%s kWh/día con paneles al máximo).",
                additionalBatteries, additionalBatteries == 1 ? "batería" : "baterías", bank.series(), bank.series(), charger.kind(), fmt(dailyRechargeKwh))
                : String.format(Locale.US, "Sin margen: %s recarga hasta ~%s kWh/día y el banco ya tiene ~%s kWh útiles.",
                charger.kind(), fmt(dailyRechargeKwh), fmt(bank.parallel() * usableKwhPerString)));
    }

    private static ExpansionAspectDto aspect(int current, int additional, String detail) {
        ExpansionLevel level = ExpansionLevel.of(current, additional);
        ExpansionAspectDto dto = new ExpansionAspectDto();
        dto.setLevel(level);
        dto.setLabel(level.getLabel());
        dto.setCurrent(current);
        dto.setAdditional(additional);
        dto.setDetail(detail);
        return dto;
    }

    // ---- Wiring ----

    /**
     * Carries the inverter's discharge current, plus the solar charge current when the charger is
     * built into the inverter; a separate controller charges the bank over its own cable instead.
     */
    private WiringSegmentDto batteryToInverterSegment(KitCompatibilityReportDto report, Inverter inverter, SolarCharger charger,
                                                      BatteryBank bank, double lengthMeters) {
        double busVolts = inverter.getBatteryVoltage();
        double dischargeA = inverter.getRatedPowerKw() * 1000 / (busVolts * INVERTER_DC_EFFICIENCY);
        double chargeA = 0;
        if (charger != null && charger.builtIn()) {
            if (charger.chargeCurrentA() == null) {
                report.warn("Falta la corriente de carga del MPPT del inversor " + inverter.getName()
                        + "; el cable de batería se calculó solo con la descarga.");
            } else {
                chargeA = charger.chargeCurrentA();
            }
        }
        double operatingA = Math.max(dischargeA, chargeA);

        WiringSegmentDto segment = sizeSegment(report, "Batería – inversor", operatingA, busVolts, lengthMeters);
        segment.setFuseNote("Fusible o breaker DC entre batería e inversor");

        if (operatingA > HIGH_CURRENT_WARNING_A && busVolts < 48) {
            // Only suggest voltages above the current one: a 12 V system can go to 24 or 48 V, a 24 V one only to 48 V.
            report.warn(String.format(Locale.US,
                    "La corriente batería–inversor llega a %s A en %s V; considera un banco de %s.",
                    fmt(operatingA), fmt(busVolts), busVolts < 24 ? "24 V o 48 V" : "48 V"));
        }
        if (bank.maxDischargeA() > 0 && dischargeA > bank.maxDischargeA()) {
            report.warn(String.format(Locale.US,
                    "El banco entrega máx. ~%s A continuos y el inversor puede pedir %s A; agrega baterías en paralelo o usa un inversor menor.",
                    fmt(bank.maxDischargeA()), fmt(dischargeA)));
        }
        return segment;
    }

    /** Separate controller only: its full charge current flows to the battery bank. */
    private WiringSegmentDto controllerToBatterySegment(KitCompatibilityReportDto report, SolarCharger charger, double lengthMeters) {
        WiringSegmentDto segment = sizeSegment(report, "Controlador – batería", charger.chargeCurrentA(), charger.busVolts(), lengthMeters);
        segment.setFuseNote("Fusible o breaker DC entre controlador y batería");
        return segment;
    }

    private WiringSegmentDto pvSegment(KitCompatibilityReportDto report, PvArray pv, SolarCharger charger, double lengthMeters) {
        double operatingA = pv.panelIscA() * pv.stringsPerChannel();
        String name = charger.builtIn() ? "Paneles – inversor (MPPT)" : "Paneles – controlador MPPT";
        WiringSegmentDto segment = sizeSegment(report, name, operatingA, pv.stringVmp(), lengthMeters);
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
