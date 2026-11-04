package com.endel.demobox.model.catalog;

/**
 * Copper DC wire sizes, thinnest to thickest. Ampacity follows a conservative low-voltage DC
 * reference table (short runs); resistance is copper at 20 C. Both are lookup data, not stored
 * per cable, so every cable of a given gauge is judged the same way.
 */
public enum WireGauge {
    AWG_14("14 AWG", 10, 0.008286),
    AWG_12("12 AWG", 15, 0.005211),
    AWG_10("10 AWG", 20, 0.003277),
    AWG_8("8 AWG", 30, 0.002061),
    AWG_6("6 AWG", 40, 0.001296),
    AWG_4("4 AWG", 50, 0.0008152),
    AWG_2("2 AWG", 70, 0.0005127),
    AWG_1_0("1/0 AWG", 100, 0.0003224),
    AWG_2_0("2/0 AWG", 150, 0.0002557),
    AWG_4_0("4/0 AWG", 200, 0.0001608);

    private final String label;
    private final double ampacityA;
    private final double ohmsPerMeter;

    WireGauge(String label, double ampacityA, double ohmsPerMeter) {
        this.label = label;
        this.ampacityA = ampacityA;
        this.ohmsPerMeter = ohmsPerMeter;
    }

    public String getLabel() {
        return label;
    }

    public double getAmpacityA() {
        return ampacityA;
    }

    public double getOhmsPerMeter() {
        return ohmsPerMeter;
    }

    public boolean isAtLeast(WireGauge other) {
        return ordinal() >= other.ordinal();
    }
}
