package com.acetelecom.repairdesk.feature.location.service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/** India dates still inside the two-day location history. Newest day first. */
public final class LocationHistoryWindow {

    static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    static final Duration RETENTION = Duration.ofDays(2);

    private LocationHistoryWindow() {
    }

    /** A point recorded before this instant is due for deletion. */
    public static Instant cutoff(Instant now) {
        return now.minus(RETENTION);
    }

    /** Today, then each older India date that still overlaps the two-day window. */
    public static List<LocalDate> days(Instant now) {
        LocalDate today = now.atZone(IST).toLocalDate();
        LocalDate oldest = cutoff(now).atZone(IST).toLocalDate();
        List<LocalDate> days = new ArrayList<>();
        for (LocalDate day = today; !day.isBefore(oldest); day = day.minusDays(1)) {
            days.add(day);
        }
        return days;
    }

    public static Instant startOfDay(LocalDate day) {
        return day.atStartOfDay(IST).toInstant();
    }

    public static ZonedDateTime india(Instant instant) {
        return instant.atZone(IST);
    }
}
