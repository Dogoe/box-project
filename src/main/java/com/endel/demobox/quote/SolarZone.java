package com.endel.demobox.quote;

import java.util.List;
import java.util.Locale;

/**
 * Areas the quote calculator offers, each with its average peak sun hours per day. Add a
 * line to {@link #ALL} to offer another area; the first one is preselected.
 */
public record SolarZone(String name, double peakSunHours) {

    public static final List<SolarZone> ALL = List.of(
            new SolarZone("Baja California", 6.0)
    );

    /** e.g. "Baja California — 6 h de sol pico". */
    public String getLabel() {
        String hours = peakSunHours == Math.rint(peakSunHours)
                ? String.format(Locale.US, "%.0f", peakSunHours)
                : String.format(Locale.US, "%.1f", peakSunHours);
        return name + " — " + hours + " h de sol pico";
    }
}
