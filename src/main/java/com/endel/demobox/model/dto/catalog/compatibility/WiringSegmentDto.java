package com.endel.demobox.model.dto.catalog.compatibility;

import com.endel.demobox.model.catalog.WireGauge;
import lombok.Data;

@Data
public class WiringSegmentDto {
    private String segment;
    /** Max current the run actually carries. */
    private double operatingCurrentA;
    /** operatingCurrentA with the 1.25 continuous-load safety factor - what the wire and fuse are sized for. */
    private double designCurrentA;
    /** One-way run length; positive + negative conductors need twice this. */
    private double lengthMeters;
    /** Null when no single gauge in the table can carry the current within the voltage-drop limit. */
    private WireGauge gauge;
    private String gaugeLabel;
    private double voltageDropPercent;
    /** Recommended protection; null when the run doesn't need one. */
    private Integer fuseA;
    private String fuseNote;
    /** Which of the kit's own cables cover this run, if any. */
    private String coveredBy;
    /** Connector on the cable, when it's known (the panels' cable: MC4 or SAE). */
    private String connector;

    public double getRequiredCableMeters() {
        return lengthMeters * 2;
    }
}
