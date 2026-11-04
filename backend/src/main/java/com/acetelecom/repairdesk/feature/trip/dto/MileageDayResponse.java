package com.acetelecom.repairdesk.feature.trip.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.acetelecom.repairdesk.feature.location.domain.VehicleType;
import com.acetelecom.repairdesk.feature.trip.domain.NoTripReason;

/** One technician's day: every trip with its route (oldest first, cancelled included) and no-trip jobs. */
public record MileageDayResponse(
        LocalDate date,
        UUID technicianId,
        String technicianName,
        VehicleType vehicleType,
        GeoPoint shop,
        MileageSummary summary,
        List<TripResponse> trips,
        List<NoTripJob> noTripJobs) {

    public record NoTripJob(
            UUID jobId,
            String referenceNumber,
            NoTripReason reasonCode,
            String reasonText,
            Instant createdAt) {
    }
}
