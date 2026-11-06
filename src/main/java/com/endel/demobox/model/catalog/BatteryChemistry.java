package com.endel.demobox.model.catalog;

public enum BatteryChemistry {
    LIFEPO4(0.90, 1.0),
    LITHIUM_ION(0.90, 1.0),
    LEAD_ACID(0.50, 0.2),
    GEL(0.50, 0.2),
    AGM(0.50, 0.2);

    private final double depthOfDischarge;
    private final double maxDischargeCRate;

    BatteryChemistry(double depthOfDischarge, double maxDischargeCRate) {
        this.depthOfDischarge = depthOfDischarge;
        this.maxDischargeCRate = maxDischargeCRate;
    }

    public double getDepthOfDischarge() {
        return depthOfDischarge;
    }

    /** Lithium batteries carry a BMS that cuts the current off above its limit; lead-acid ones don't. */
    public boolean hasBms() {
        return this == LIFEPO4 || this == LITHIUM_ION;
    }

    /** Continuous discharge current as a multiple of rated Ah (1.0 = 1C). */
    public double getMaxDischargeCRate() {
        return maxDischargeCRate;
    }
}
