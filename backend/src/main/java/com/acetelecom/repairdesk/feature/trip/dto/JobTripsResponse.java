package com.acetelecom.repairdesk.feature.trip.dto;

import java.time.Instant;
import java.util.List;

import com.acetelecom.repairdesk.feature.trip.domain.NoTripReason;

/** Trips for one job, oldest first, and the latest no-trip reason if the job started without one. */
public record JobTripsResponse(List<TripResponse> trips, NoTrip noTrip) {

    public record NoTrip(NoTripReason reasonCode, String reasonText, String technicianName, Instant createdAt) {
    }
}
