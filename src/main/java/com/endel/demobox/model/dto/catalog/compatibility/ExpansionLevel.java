package com.endel.demobox.model.dto.catalog.compatibility;

public enum ExpansionLevel {
    EXPANDABLE("Ampliable", "Ampliable"),
    LIMITED("Ampliación limitada", "Ampliación limitada"),
    MAXED("Al máximo", "Sin margen de ampliación");

    private final String label;
    private final String overallLabel;

    ExpansionLevel(String label, String overallLabel) {
        this.label = label;
        this.overallLabel = overallLabel;
    }

    public String getLabel() {
        return label;
    }

    /** Wording for the kit as a whole ("Sin margen de ampliación" instead of "Al máximo"). */
    public String getOverallLabel() {
        return overallLabel;
    }

    /** No room at all -> maxed; room to more than double what the kit has -> expandable; else limited. */
    public static ExpansionLevel of(int current, int additional) {
        if (additional <= 0) {
            return MAXED;
        }
        return additional > current ? EXPANDABLE : LIMITED;
    }
}
