package com.acetelecom.repairdesk.feature.trip.dto;

import java.time.LocalDate;
import java.util.UUID;

import com.acetelecom.repairdesk.feature.location.domain.VehicleType;

/**
 * One technician on one India date ({@code view=DAY}) or one month ({@code view=MONTH}).
 * Distances count ENDED trips only. {@code ratePaise} is null when the trips used different rates.
 */
public record MileageRow(
        LocalDate date,
        String month,
        UUID technicianId,
        String technicianName,
        VehicleType vehicleType,
        int trips,
        long trackedDistanceM,
        long adjustmentM,
        long payableDistanceM,
        Integer ratePaise,
        long amountPaise,
        int flaggedTrips,
        int noTripJobs,
        int activeTrips) {
}
