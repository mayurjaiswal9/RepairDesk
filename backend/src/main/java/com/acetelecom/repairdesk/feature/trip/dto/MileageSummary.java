package com.acetelecom.repairdesk.feature.trip.dto;

public record MileageSummary(
        long payableDistanceM,
        int trips,
        long amountPaise,
        int flaggedTrips,
        int noTripJobs,
        int activeTrips) {
}
