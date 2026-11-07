package com.endel.demobox.service.catalog;

import com.endel.demobox.model.catalog.Battery;
import com.endel.demobox.model.catalog.FuseClass;
import com.endel.demobox.model.catalog.Protection;
import com.endel.demobox.model.catalog.ProtectionType;
import com.endel.demobox.model.dto.catalog.compatibility.KitCompatibilityReportDto;
import com.endel.demobox.model.dto.catalog.compatibility.ProtectionCheckDto;
import com.endel.demobox.model.dto.catalog.compatibility.ProtectionSlot;
import com.endel.demobox.model.dto.catalog.compatibility.SuggestedItemDto;
import com.endel.demobox.model.dto.catalog.compatibility.WiringSegmentDto;
import com.endel.demobox.repository.catalog.ProtectionRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Works out which fuses, breakers and disconnects a kit needs from the numbers the kit review
 * already computed (each run's current and cable, the bank's voltage, the panels' cold voltage, the
 * inverter's power), checks the kit's protection products against them, and suggests the cheapest
 * catalog product for whatever is missing.
 */
@Component
public class KitProtectionPlanner {

    /** A lithium bank's charge voltage is about 1.2 x its nominal voltage (14.4 / 28.8 / 57.6 V). */
    static final double CHARGE_VOLTAGE_FACTOR = 1.2;
    /** Above this a lithium bank on 12 or 24 V also needs a Class T fuse. */
    static final double CLASS_T_ABOVE_A = 300;
    /** The inverter's AC output in Mexico. */
    static final double AC_VOLTS = 120;
    static final double CONTINUOUS_FACTOR = 1.25;

    private static final Set<FuseClass> BATTERY_FUSES = EnumSet.of(FuseClass.MRBF, FuseClass.ANL, FuseClass.CLASS_T, FuseClass.OTHER);
    private static final Set<FuseClass> CLASS_T_ONLY = EnumSet.of(FuseClass.CLASS_T);
    private static final Set<FuseClass> PV_FUSES = EnumSet.of(FuseClass.GPV);

    private final ProtectionRepository protectionRepository;

    public KitProtectionPlanner(ProtectionRepository protectionRepository) {
        this.protectionRepository = protectionRepository;
    }

    /** What the kit review knows about the kit. Runs and battery are null when the kit doesn't have them. */
    public record Input(double busVolts,
                        WiringSegmentDto batteryRun, Battery battery, int parallelStrings,
                        WiringSegmentDto controllerRun,
                        WiringSegmentDto pvRun, int pvStrings, int pvStringsPerInput, int pvInputs, double pvColdStringVoc,
                        double panelIscA,
                        double inverterKw) {
    }

    /** Which cable run a protection is wired into (its terminals are counted on that run's cable). */
    enum Run { BATTERY, CONTROLLER, OTHER }

    /** The protections that end up on each run: the kit's own, or the suggested ones when it lacks them. */
    public record ProtectionPlan(List<Protection> batteryRun, List<Protection> controllerRun) {
        static ProtectionPlan empty() {
            return new ProtectionPlan(List.of(), List.of());
        }
    }

    record Requirement(ProtectionSlot slot, String label, int quantity, Set<ProtectionType> types, double minA, double maxA, double minV,
                       int minPoles, Set<FuseClass> fuseClasses, Run run) {

        Requirement(ProtectionSlot slot, String label, int quantity, Set<ProtectionType> types, double minA, double maxA, double minV,
                    int minPoles, Set<FuseClass> fuseClasses) {
            this(slot, label, quantity, types, minA, maxA, minV, minPoles, fuseClasses, Run.OTHER);
        }

        boolean isMetBy(Protection p) {
            return problemWith(p) == null;
        }

        /** Why {@code p} doesn't meet this requirement, or null when it does. */
        String problemWith(Protection p) {
            if (!types.contains(p.getProtectionType())) {
                return p.getName() + " es " + p.getProtectionType().getLabel().toLowerCase(Locale.ROOT);
            }
            if (p.getProtectionType() == ProtectionType.FUSE && (p.getFuseClass() == null || !fuseClasses.contains(p.getFuseClass()))) {
                return p.getName() + ": se necesita fusible " + classes(fuseClasses)
                        + (p.getFuseClass() == null ? "" : " y es " + p.getFuseClass().getLabel());
            }
            if (p.getRatedCurrentA() + 1e-9 < minA) {
                return p.getName() + ": es de " + fmt(p.getRatedCurrentA()) + " A y se necesitan " + fmt(minA) + " A o más";
            }
            if (p.getRatedCurrentA() - 1e-9 > maxA) {
                return p.getName() + ": es de " + fmt(p.getRatedCurrentA()) + " A, más de lo que aguanta el cable ("
                        + fmt(maxA) + " A); no lo protegería";
            }
            if (p.getMaxVoltage() + 1e-9 < minV) {
                return p.getName() + ": aguanta " + fmt(p.getMaxVoltage()) + " V y se necesitan " + fmt(minV) + " V o más";
            }
            if (p.getPolesOrOne() < minPoles) {
                return p.getName() + ": es de " + p.getPolesOrOne() + " polo(s) y se necesitan " + minPoles;
            }
            return null;
        }

        String describe() {
            boolean ac = types.contains(ProtectionType.AC_BREAKER);
            String amps = maxA == Double.MAX_VALUE ? fmt(minA) + " A o más"
                    : Math.abs(maxA - minA) < 1e-9 ? fmt(minA) + " A" : "de " + fmt(minA) + " a " + fmt(maxA) + " A";
            StringBuilder text = new StringBuilder(amps).append(", ").append(fmt(minV)).append(ac ? " V AC" : " V DC").append(" o más");
            if (types.contains(ProtectionType.FUSE) && fuseClasses.size() < BATTERY_FUSES.size()) {
                text.append(", ").append(classes(fuseClasses));
            }
            if (minPoles > 1) {
                text.append(", ").append(minPoles).append(" polos");
            }
            return text.toString();
        }
    }

    public ProtectionPlan plan(KitCompatibilityReportDto report, Input in, List<KitLine> protectionLines) {
        List<Requirement> requirements = new ArrayList<>();
        double chargeVolts = round1(in.busVolts() * CHARGE_VOLTAGE_FACTOR);

        WiringSegmentDto batteryRun = in.batteryRun();
        if (batteryRun != null && batteryRun.getGauge() != null && in.battery() != null) {
            double designA = batteryRun.getDesignCurrentA();
            double cableA = cableAmpacity(batteryRun);
            Battery battery = in.battery();
            boolean lithium = battery.getChemistry().hasBms();
            // A lithium bank's very low internal resistance makes its short-circuit current huge (thousands of
            // amps). On 48 V, MRBF and ANL fuses can't interrupt that safely, so it needs Class T. On 12 and 24 V a
            // single string is fine with MRBF/ANL (they are inside their voltage and interrupt ratings), but batteries
            // in parallel (the short grows with each string) or a very high current still need Class T.
            boolean classT = lithium && (in.busVolts() >= 48 || in.parallelStrings() >= 2 || designA > CLASS_T_ABOVE_A);
            Set<FuseClass> fuseClasses = classT ? CLASS_T_ONLY : BATTERY_FUSES;
            Double builtIn = battery.getIntegratedBreakerA();

            if (builtIn != null) {
                // The battery's own breaker switches it off; it also protects the cable when there's a
                // single battery string and it isn't bigger than what the cable carries.
                ProtectionCheckDto disconnect = coveredRow(ProtectionSlot.BATTERY_SWITCH, "Desconexión de batería", "Breaker integrado de la batería ("
                        + fmt(builtIn) + " A)", in.parallelStrings());
                report.getProtections().add(disconnect);
                if (builtIn + 1e-9 < batteryRun.getOperatingCurrentA()) {
                    report.warn(String.format(Locale.US, "El breaker integrado de la batería (%s A) puede botarse: el tramo batería–inversor llega a %s A.",
                            fmt(builtIn), fmt(batteryRun.getOperatingCurrentA())));
                }
                if (in.parallelStrings() == 1 && builtIn <= cableA + 1e-9) {
                    report.getProtections().add(coveredRow(ProtectionSlot.MAIN_FUSE, "Fusible principal de batería", "Breaker integrado de la batería ("
                            + fmt(builtIn) + " A)", 1));
                } else {
                    requirements.add(new Requirement(ProtectionSlot.MAIN_FUSE, "Fusible principal de batería", 1, EnumSet.of(ProtectionType.FUSE),
                            designA, cableA, chargeVolts, 1, fuseClasses, Run.BATTERY));
                }
            } else {
                requirements.add(new Requirement(ProtectionSlot.MAIN_FUSE, "Fusible principal de batería", 1, EnumSet.of(ProtectionType.FUSE),
                        designA, cableA, chargeVolts, 1, fuseClasses, Run.BATTERY));
                requirements.add(new Requirement(ProtectionSlot.BATTERY_SWITCH, "Interruptor / breaker DC de batería", 1, EnumSet.of(ProtectionType.DC_BREAKER),
                        designA, Double.MAX_VALUE, chargeVolts, 1, BATTERY_FUSES, Run.BATTERY));
            }
        }

        WiringSegmentDto controllerRun = in.controllerRun();
        if (controllerRun != null && controllerRun.getGauge() != null) {
            requirements.add(new Requirement(ProtectionSlot.CONTROLLER, "Protección controlador – batería", 1, EnumSet.of(ProtectionType.FUSE, ProtectionType.DC_BREAKER),
                    controllerRun.getDesignCurrentA(), cableAmpacity(controllerRun), chargeVolts, 1, BATTERY_FUSES, Run.CONTROLLER));
        }

        WiringSegmentDto pvRun = in.pvRun();
        if (pvRun != null && in.pvStrings() > 0) {
            int inputsUsed = Math.max(1, Math.min(in.pvStrings(), in.pvInputs()));
            requirements.add(new Requirement(ProtectionSlot.PV_DISCONNECT, "Desconectador DC de paneles", inputsUsed, EnumSet.of(ProtectionType.PV_DISCONNECT),
                    round1(in.panelIscA() * CONTINUOUS_FACTOR * in.pvStringsPerInput()), Double.MAX_VALUE,
                    round1(in.pvColdStringVoc()), 2, BATTERY_FUSES));
            if (pvRun.getFuseA() != null && in.pvStringsPerInput() >= KitCompatibilityService.MIN_PARALLEL_STRINGS_NEEDING_FUSE
                    && pvRun.getGauge() != null) {
                requirements.add(new Requirement(ProtectionSlot.PV_STRING_FUSE, "Fusible por cadena de paneles", in.pvStrings(), EnumSet.of(ProtectionType.FUSE),
                        pvRun.getFuseA(), cableAmpacity(pvRun), round1(in.pvColdStringVoc()), 1, PV_FUSES));
            }
        }

        if (in.inverterKw() > 0) {
            double outputDesignA = in.inverterKw() * 1000 / AC_VOLTS * CONTINUOUS_FACTOR;
            Integer size = KitCompatibilityService.standardFuse(outputDesignA);
            if (size != null) {
                // The standard size, or one size up if that's what the catalog has.
                requirements.add(new Requirement(ProtectionSlot.AC_BREAKER, "Breaker AC de salida del inversor", 1, EnumSet.of(ProtectionType.AC_BREAKER),
                        size, nextStandardSize(size), AC_VOLTS, 1, BATTERY_FUSES));
            }
        }

        return cover(report, requirements, protectionLines);
    }

    private ProtectionPlan cover(KitCompatibilityReportDto report, List<Requirement> requirements, List<KitLine> protectionLines) {
        List<Protection> onBatteryRun = new ArrayList<>();
        List<Protection> onControllerRun = new ArrayList<>();
        List<Pool> pools = protectionLines.stream()
                .map(line -> new Pool((Protection) line.product(), line.quantity()))
                .sorted(Comparator.comparingDouble(p -> p.protection.getRatedCurrentA()))
                .toList();
        List<Protection> catalog = null;

        for (Requirement requirement : requirements) {
            ProtectionCheckDto row = new ProtectionCheckDto();
            row.setSlot(requirement.slot());
            row.setLabel(requirement.label());
            row.setRequirement(requirement.describe());
            row.setQuantity(requirement.quantity());

            int missing = requirement.quantity();
            List<String> used = new ArrayList<>();
            for (Pool pool : pools) {
                if (missing == 0) {
                    break;
                }
                if (pool.remaining > 0 && requirement.isMetBy(pool.protection)) {
                    int take = Math.min(pool.remaining, missing);
                    pool.remaining -= take;
                    missing -= take;
                    used.add(take + " × " + pool.protection.getName());
                    if (row.getProduct() == null) {
                        row.setProduct(pool.protection);
                    }
                    record(requirement, pool.protection, take, onBatteryRun, onControllerRun);
                }
            }
            row.setCovered(missing == 0);
            row.setCoveredBy(used.isEmpty() ? null : String.join(", ", used));

            if (missing > 0) {
                pools.stream()
                        .filter(p -> p.remaining > 0 && requirement.types().contains(p.protection.getProtectionType()))
                        .map(p -> requirement.problemWith(p.protection))
                        .filter(problem -> problem != null)
                        .findFirst()
                        .ifPresent(row::setProblem);
                if (catalog == null) {
                    catalog = protectionRepository.findAll();
                }
                Optional<Protection> best = catalog.stream()
                        .filter(p -> p.isActive() && requirement.isMetBy(p))
                        .min(Comparator.comparing(Protection::getPrice).thenComparingDouble(Protection::getRatedCurrentA));
                final int toAdd = missing;
                best.ifPresent(p -> record(requirement, p, toAdd, onBatteryRun, onControllerRun));
                if (row.getProduct() == null) {
                    best.ifPresent(row::setProduct);
                }
                best.ifPresent(p -> {
                    SuggestedItemDto suggestion = new SuggestedItemDto(p.getId(), p.getSku(), p.getName(), toAdd, p.getPrice(), requirement.label());
                    row.setSuggestion(suggestion);
                    report.suggest(new SuggestedItemDto(p.getId(), p.getSku(), p.getName(), toAdd, p.getPrice(), requirement.label()));
                });
                report.warn("Falta " + (requirement.quantity() > 1 ? missing + " × " : "") + lowerFirst(requirement.label())
                        + " (" + requirement.describe() + ")."
                        + (row.getProblem() != null ? " " + row.getProblem() + "." : "")
                        + best.map(p -> " Sugerencia: " + p.getName() + ".")
                              .orElse(" No hay en tu catálogo una protección que cumpla; dala de alta en Protecciones."));
            }
            report.getProtections().add(row);
        }

        for (Pool pool : pools) {
            if (pool.remaining > 0) {
                report.warn(pool.remaining + " × " + pool.protection.getName()
                        + " no corresponde a ninguna protección que necesite este kit; revisa si sobra o si sus datos son correctos.");
            }
        }
        return new ProtectionPlan(onBatteryRun, onControllerRun);
    }

    private static void record(Requirement requirement, Protection protection, int count,
                               List<Protection> onBatteryRun, List<Protection> onControllerRun) {
        List<Protection> target = switch (requirement.run()) {
            case BATTERY -> onBatteryRun;
            case CONTROLLER -> onControllerRun;
            case OTHER -> null;
        };
        if (target != null) {
            for (int i = 0; i < count; i++) {
                target.add(protection);
            }
        }
    }

    /**
     * What a fuse on this run may be at most: the ampacity of the cable the kit actually uses (its thinnest piece),
     * or the minimum cable the run needs while the kit doesn't include enough cable for it.
     */
    private static double cableAmpacity(WiringSegmentDto run) {
        return (run.getWeakestCableGauge() != null ? run.getWeakestCableGauge() : run.getGauge()).getAmpacityA();
    }

    private static ProtectionCheckDto coveredRow(ProtectionSlot slot, String label, String coveredBy, int quantity) {
        ProtectionCheckDto row = new ProtectionCheckDto();
        row.setSlot(slot);
        row.setBuiltIntoBattery(true);
        row.setLabel(label);
        row.setRequirement("Incluida en la batería");
        row.setQuantity(quantity);
        row.setCovered(true);
        row.setCoveredBy(coveredBy);
        return row;
    }

    private static double nextStandardSize(int size) {
        int[] sizes = KitCompatibilityService.STANDARD_FUSES_A;
        for (int i = 0; i < sizes.length - 1; i++) {
            if (sizes[i] == size) {
                return sizes[i + 1];
            }
        }
        return size;
    }

    private static final class Pool {
        final Protection protection;
        int remaining;

        Pool(Protection protection, int remaining) {
            this.protection = protection;
            this.remaining = remaining;
        }
    }

    /** "Breaker AC de salida" -> "breaker AC de salida" (keeps acronyms like DC/AC). */
    private static String lowerFirst(String text) {
        return text.isEmpty() ? text : Character.toLowerCase(text.charAt(0)) + text.substring(1);
    }

    private static String classes(Set<FuseClass> classes) {
        return String.join(" o ", classes.stream().map(FuseClass::getLabel).toList());
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    static String fmt(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.format(Locale.US, "%.1f", value);
    }
}
