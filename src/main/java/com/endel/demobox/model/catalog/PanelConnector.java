package com.endel.demobox.model.catalog;

import lombok.Getter;

/** Connector on a solar panel's output cable: MC4 on regular panels, SAE on small 12 V kits. */
@Getter
public enum PanelConnector {
    MC4("MC4"),
    SAE("SAE");

    private final String label;

    PanelConnector(String label) {
        this.label = label;
    }
}
