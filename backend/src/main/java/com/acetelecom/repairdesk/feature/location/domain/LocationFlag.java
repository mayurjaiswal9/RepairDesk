package com.acetelecom.repairdesk.feature.location.domain;

/** How a start or end fix compared with the shop. */
public enum LocationFlag {
    INSIDE("Inside shop"),
    OUTSIDE("Outside shop"),
    UNCERTAIN("Location uncertain"),
    MISSING("Location missing"),
    MOCKED("Mocked location");

    private final String label;

    LocationFlag(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
