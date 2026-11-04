package com.acetelecom.repairdesk.feature.trip.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.acetelecom.repairdesk.feature.location.domain.VehicleType;
import com.acetelecom.repairdesk.feature.trip.domain.TripEndReason;
import com.acetelecom.repairdesk.feature.trip.domain.TripStartKind;
import com.acetelecom.repairdesk.feature.trip.domain.TripStatus;

/**
 * One trip. {@code route} is [[lat, lng], ...] and is empty except in the job
 * travel and day detail responses. {@code amountPaise} is null while ACTIVE.
 */
public record TripResponse(
        UUID id,
        UUID jobId,
        String jobReference,
        UUID technicianId,
        String technicianName,
        TripStatus status,
        Instant startedAt,
        Instant endedAt,
        long durationSeconds,
        TripStartKind startKind,
        String startJobReference,
        GeoPoint start,
        GeoPoint end,
        GeoPoint destination,
        int trackedDistanceM,
        int estimatedGapM,
        Integer plannedDistanceM,
        Integer plannedDurationSeconds,
        boolean plannedEstimated,
        Integer adjustedDistanceM,
        String adjustReason,
        String adjustedByName,
        Instant adjustedAt,
        int payableDistanceM,
        VehicleType vehicleType,
        Integer ratePaise,
        Integer amountPaise,
        List<String> flags,
        int pointCount,
        TripEndReason endReason,
        List<double[]> route) {
}
