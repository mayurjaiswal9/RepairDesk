package com.acetelecom.repairdesk.feature.location.domain;

/** Why a work day closed. */
public enum EndReason {
    MANUAL("Manual"),
    AUTO("Auto");

    private final String label;

    EndReason(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
