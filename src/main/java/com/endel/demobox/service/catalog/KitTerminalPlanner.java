package com.endel.demobox.service.catalog;

import com.endel.demobox.model.catalog.Battery;
import com.endel.demobox.model.catalog.FuseClass;
import com.endel.demobox.model.catalog.Inverter;
import com.endel.demobox.model.catalog.Protection;
import com.endel.demobox.model.catalog.ProtectionType;
import com.endel.demobox.model.catalog.Terminal;
import com.endel.demobox.model.catalog.TerminalSize;
import com.endel.demobox.model.catalog.WireGauge;
import com.endel.demobox.model.dto.catalog.compatibility.KitCompatibilityReportDto;
import com.endel.demobox.model.dto.catalog.compatibility.SuggestedItemDto;
import com.endel.demobox.model.dto.catalog.compatibility.TerminalNeedDto;
import com.endel.demobox.model.dto.catalog.compatibility.WiringSegmentDto;
import com.endel.demobox.repository.catalog.TerminalRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Counts the crimp lugs the kit's heavy DC cables need. Each run has a positive and a negative
 * conductor with two ends each, and every protection wired in series on the positive cuts it once
 * more (two extra ends) - except an MRBF fuse, which sits on the battery post under the cable's own
 * lug. Each lug matches the run's cable gauge and the stud of what it bolts onto. Panels use MC4
 * connectors and the AC side uses the breaker's clamps, so neither is counted; neither are the
 * charge controller's own clamps. Informational only: it never adds warnings.
 */
@Component
public class KitTerminalPlanner {

    private final TerminalRepository terminalRepository;

    public KitTerminalPlanner(TerminalRepository terminalRepository) {
        this.terminalRepository = terminalRepository;
    }

    private record Kind(WireGauge gauge, TerminalSize size) {
    }

    private static final class Need {
        int count;
        final Map<String, Integer> places = new LinkedHashMap<>();

        void add(int n, String place) {
            count += n;
            places.merge(place, n, Integer::sum);
        }
    }

    public void plan(KitCompatibilityReportDto report, Battery battery, Inverter inverter,
                     WiringSegmentDto batteryRun, WiringSegmentDto controllerRun,
                     KitProtectionPlanner.ProtectionPlan protections, List<KitLine> terminalLines) {
        if (battery == null) {
            return;
        }
        Map<Kind, Need> needs = new LinkedHashMap<>();

        WireGauge batteryGauge = gaugeOf(batteryRun);
        if (batteryGauge != null) {
            add(needs, batteryGauge, battery.getTerminalSize(), 2, "lado batería");
            add(needs, batteryGauge, inverter.getTerminalSize(), 2, "lado inversor");
            for (Protection protection : protections.batteryRun()) {
                addInSeries(needs, batteryGauge, protection);
            }
        }

        WireGauge controllerGauge = gaugeOf(controllerRun);
        if (controllerGauge != null) {
            add(needs, controllerGauge, battery.getTerminalSize(), 2, "controlador → batería, lado batería");
            for (Protection protection : protections.controllerRun()) {
                addInSeries(needs, controllerGauge, protection);
            }
        }

        cover(report, needs, terminalLines);
    }

    private static WireGauge gaugeOf(WiringSegmentDto run) {
        if (run == null) {
            return null;
        }
        return run.getCableGauge() != null ? run.getCableGauge() : run.getGauge();
    }

    private static void addInSeries(Map<Kind, Need> needs, WireGauge gauge, Protection protection) {
        if (protection.getProtectionType() == ProtectionType.FUSE && protection.getFuseClass() == FuseClass.MRBF) {
            return; // bolts onto the battery post; the cable's lug goes on top of it
        }
        String place = protection.getProtectionType() == ProtectionType.FUSE
                ? "portafusible " + (protection.getFuseClass() == null ? "" : protection.getFuseClass().getLabel())
                : protection.getProtectionType() == ProtectionType.DC_BREAKER ? "breaker DC" : protection.getProtectionType().getLabel();
        add(needs, gauge, protection.getTerminalSize(), 2, place.trim());
    }

    private static void add(Map<Kind, Need> needs, WireGauge gauge, TerminalSize size, int count, String place) {
        needs.computeIfAbsent(new Kind(gauge, TerminalSize.orDefault(size)), k -> new Need()).add(count, place);
    }

    private void cover(KitCompatibilityReportDto report, Map<Kind, Need> needs, List<KitLine> terminalLines) {
        Map<Kind, Integer> piecesInKit = new LinkedHashMap<>();
        Map<Kind, List<String>> namesInKit = new LinkedHashMap<>();
        for (KitLine line : terminalLines) {
            Terminal terminal = (Terminal) line.product();
            Kind kind = new Kind(terminal.getGauge(), terminal.getTerminalSize());
            piecesInKit.merge(kind, line.quantity() * terminal.getPiecesPerPack(), Integer::sum);
            namesInKit.computeIfAbsent(kind, k -> new ArrayList<>()).add(line.quantity() + " × " + terminal.getName());
        }

        List<Terminal> catalog = null;
        for (Map.Entry<Kind, Need> entry : needs.entrySet()) {
            Kind kind = entry.getKey();
            Need need = entry.getValue();
            TerminalNeedDto row = new TerminalNeedDto();
            row.setGaugeLabel(kind.gauge().getLabel());
            row.setSizeLabel(kind.size().getLabel());
            row.setQuantity(need.count);
            row.setPlaces(String.join(", ", need.places.entrySet().stream().map(e -> e.getValue() + " " + e.getKey()).toList()));

            int inKit = piecesInKit.getOrDefault(kind, 0);
            row.setCovered(inKit >= need.count);
            row.setCoveredBy(namesInKit.containsKey(kind) ? String.join(", ", namesInKit.get(kind)) : null);

            int missing = need.count - inKit;
            if (missing > 0) {
                if (catalog == null) {
                    catalog = terminalRepository.findAll();
                }
                catalog.stream()
                        .filter(t -> t.isActive() && t.getGauge() == kind.gauge() && t.getTerminalSize() == kind.size())
                        .min(Comparator.comparing((Terminal t) -> packCost(t, missing)).thenComparingInt(Terminal::getPiecesPerPack))
                        .ifPresent(t -> {
                            int packs = packsFor(t, missing);
                            String reason = "Terminales " + kind.gauge().getLabel() + " " + kind.size().getLabel();
                            row.setSuggestion(new SuggestedItemDto(t.getId(), t.getSku(), t.getName(), packs, t.getPrice(), reason));
                            report.suggest(new SuggestedItemDto(t.getId(), t.getSku(), t.getName(), packs, t.getPrice(), reason));
                        });
            }
            report.getTerminals().add(row);
        }
    }

    private static int packsFor(Terminal terminal, int pieces) {
        int perPack = Math.max(1, terminal.getPiecesPerPack());
        return (pieces + perPack - 1) / perPack;
    }

    private static BigDecimal packCost(Terminal terminal, int pieces) {
        return Objects.requireNonNullElse(terminal.getPrice(), BigDecimal.ZERO).multiply(BigDecimal.valueOf(packsFor(terminal, pieces)));
    }
}
