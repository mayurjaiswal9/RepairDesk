package com.acetelecom.repairdesk.feature.job.dto;

import java.time.LocalDate;
import java.util.List;

/** Open jobs created before today (India), grouped by the day they were created. */
public record OpenJobCarryoverResponse(List<Day> days) {

    public record Day(LocalDate date, long count) {
    }
}
