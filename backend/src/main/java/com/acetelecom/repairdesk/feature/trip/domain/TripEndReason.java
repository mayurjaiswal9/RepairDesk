package com.acetelecom.repairdesk.feature.trip.domain;

/** Why a trip stopped recording. Only ARRIVED, GEOFENCE and CANCELLED come from the app. */
public enum TripEndReason {
    ARRIVED("Arrived"),
    GEOFENCE("At customer"),
    JOB_STARTED("Job started"),
    REPLACED("New trip started"),
    TIMEOUT("Timed out"),
    CANCELLED("Cancelled");

    private final String label;

    TripEndReason(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
