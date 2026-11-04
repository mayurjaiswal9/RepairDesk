package com.acetelecom.repairdesk.feature.location.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One technician on one India date. Coordinates are omitted on purpose.
 * Status is NOT_STARTED, ON_DUTY, ENDED, or AUTO_ENDED.
 */
public record AttendanceRowResponse(
        UUID technicianId,
        String technicianName,
        LocalDate workDate,
        String status,
        Instant startAt,
        Instant endAt,
        String startFlag,
        String endReason,
        Long durationMinutes) {
}
