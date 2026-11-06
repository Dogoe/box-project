package com.endel.demobox.model.catalog;

/**
 * Copper DC wire sizes, thinnest to thickest. Ampacity is the NEC 310.16 copper 75 C column
 * (THHN/THWN and battery cable), with the small-conductor overcurrent limits of NEC 240.4(D) for
 * 14, 12 and 10 AWG; resistance is copper at 20 C. Both are lookup data, not stored per cable, so
 * every cable of a given gauge is judged the same way.
 */
public enum WireGauge {
    AWG_14("14 AWG", 15, 0.008286),
    AWG_12("12 AWG", 20, 0.005211),
    AWG_10("10 AWG", 30, 0.003277),
    AWG_8("8 AWG", 50, 0.002061),
    AWG_6("6 AWG", 65, 0.001296),
    AWG_4("4 AWG", 85, 0.0008152),
    AWG_2("2 AWG", 115, 0.0005127),
    AWG_1_0("1/0 AWG", 150, 0.0003224),
    AWG_2_0("2/0 AWG", 175, 0.0002557),
    AWG_4_0("4/0 AWG", 230, 0.0001608);

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
