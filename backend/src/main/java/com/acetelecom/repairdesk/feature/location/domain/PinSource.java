package com.acetelecom.repairdesk.feature.location.domain;

public enum PinSource {
    GEOCODED,
    MANUAL_PIN,
    CHECK_IN,
    /** Coordinates taken from a Maps link the customer sent. */
    CUSTOMER_LINK
}
