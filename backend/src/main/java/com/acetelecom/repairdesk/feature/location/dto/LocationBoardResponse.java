package com.acetelecom.repairdesk.feature.location.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Office, technician positions, and travel time to open jobs. */
public record LocationBoardResponse(
        Shop shop,
        List<TechnicianMarker> technicians,
        List<JobMarker> jobs) {

    public record Shop(Double latitude, Double longitude, int radiusM) {
    }

    public record TechnicianMarker(
            UUID id,
            String name,
            Double latitude,
            Double longitude,
            Instant recordedAt,
            UUID jobId,
            String state) {
    }

    public record JobMarker(
            UUID id,
            String referenceNumber,
            String address,
            Double latitude,
            Double longitude,
            UUID technicianId,
            String technicianName,
            Integer etaMinutes,
            Integer distanceMetres,
            boolean etaEstimated,
            String etaFrom) {
    }
}
