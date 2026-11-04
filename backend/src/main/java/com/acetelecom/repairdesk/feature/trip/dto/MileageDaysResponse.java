package com.acetelecom.repairdesk.feature.trip.dto;

import java.time.LocalDate;
import java.util.List;

/** {@code summary} covers the rows after the show filter. */
public record MileageDaysResponse(
        LocalDate from,
        LocalDate to,
        String view,
        MileageSummary summary,
        List<MileageRow> rows) {
}
