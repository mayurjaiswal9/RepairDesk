package com.acetelecom.repairdesk.feature.location.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Shared positions for one India day, newest first. {@code days} is the filter. */
public record LocationHistoryResponse(
        LocalDate date,
        List<LocalDate> days,
        List<Entry> locations) {

    public record Entry(
            UUID id,
            UUID technicianId,
            String technicianName,
            double latitude,
            double longitude,
            Instant recordedAt,
            UUID jobId,
            String jobReference,
            String state) {
    }
}
