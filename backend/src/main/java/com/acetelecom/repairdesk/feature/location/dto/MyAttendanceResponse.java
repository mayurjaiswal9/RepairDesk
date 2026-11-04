package com.acetelecom.repairdesk.feature.location.dto;

import java.time.Instant;
import java.time.LocalDate;

/** The signed-in technician's work day for today in India. */
public record MyAttendanceResponse(
        boolean started,
        LocalDate workDate,
        Instant startAt,
        Instant endAt,
        String startFlag,
        String endFlag,
        String endReason) {
}
