package com.acetelecom.repairdesk.feature.trip.domain;

/** Why a technician started a job without a trip. OTHER needs a note. */
public enum NoTripReason {
    AT_SHOP("Customer came to the shop"),
    ALREADY_ON_SITE("Already at this site"),
    PHONE_PROBLEM("Phone or GPS problem"),
    OTHER("Other");

    private final String label;

    NoTripReason(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
