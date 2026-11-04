package com.acetelecom.repairdesk.common.time;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Shop calendar dates. Lists and the open-job warning use India time,
 * not the server or the browser clock.
 */
public final class IndiaDay {

    public static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    private IndiaDay() {
    }

    public record Range(boolean hasFrom, Instant fromInclusive, boolean hasTo, Instant toExclusive) {
    }

    /** Inclusive calendar dates. A reversed range is swapped. Null ends stay open. */
    public static Range range(LocalDate from, LocalDate to) {
        if (from != null && to != null && to.isBefore(from)) {
            LocalDate swap = from;
            from = to;
            to = swap;
        }
        return new Range(
                from != null,
                from == null ? Instant.EPOCH : from.atStartOfDay(ZONE).toInstant(),
                to != null,
                to == null ? Instant.EPOCH : to.plusDays(1).atStartOfDay(ZONE).toInstant());
    }

    public static Instant startOfToday() {
        return LocalDate.now(ZONE).atStartOfDay(ZONE).toInstant();
    }
}
