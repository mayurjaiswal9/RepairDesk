package com.acetelecom.repairdesk.feature.location.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

class LocationHistoryWindowTest {

    @Test
    void twoDaysReachBackIntoTheThirdIndiaDate() {
        Instant now = Instant.parse("2026-11-06T05:00:00Z");
        assertEquals(Instant.parse("2026-11-04T05:00:00Z"), LocationHistoryWindow.cutoff(now));
        assertEquals(List.of(
                LocalDate.of(2026, 11, 6),
                LocalDate.of(2026, 11, 5),
                LocalDate.of(2026, 11, 4)), LocationHistoryWindow.days(now));
    }

    @Test
    void midnightStillKeepsYesterdayAndTheDayBefore() {
        Instant now = Instant.parse("2026-11-05T18:30:00Z");
        assertEquals(List.of(
                LocalDate.of(2026, 11, 6),
                LocalDate.of(2026, 11, 5),
                LocalDate.of(2026, 11, 4)), LocationHistoryWindow.days(now));
        assertEquals(Instant.parse("2026-11-03T18:30:00Z"), LocationHistoryWindow.startOfDay(LocalDate.of(2026, 11, 4)));
    }
}
