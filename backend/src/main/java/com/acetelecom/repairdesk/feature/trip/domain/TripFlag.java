package com.acetelecom.repairdesk.feature.trip.domain;

/** Reasons Staff may want to check a trip. A flagged trip is still payable. */
public enum TripFlag {
    NO_POINTS("No points"),
    LONGER_THAN_ROUTE("Longer than route"),
    SHORTER_THAN_ROUTE("Shorter than route"),
    MOCKED("Mocked location"),
    GAPS("Partly estimated"),
    FAR_FROM_JOB("Ended far from job");

    private final String label;

    TripFlag(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
