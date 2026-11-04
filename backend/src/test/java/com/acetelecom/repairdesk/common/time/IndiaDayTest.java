package com.acetelecom.repairdesk.common.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class IndiaDayTest {

    @Test
    void oneIndiaDayRunsFromMidnightToTheNextMidnight() {
        IndiaDay.Range range = IndiaDay.range(LocalDate.of(2026, 11, 4), LocalDate.of(2026, 11, 4));

        assertThat(range.hasFrom()).isTrue();
        assertThat(range.hasTo()).isTrue();
        assertThat(range.fromInclusive()).isEqualTo(Instant.parse("2026-11-03T18:30:00Z"));
        assertThat(range.toExclusive()).isEqualTo(Instant.parse("2026-11-04T18:30:00Z"));
    }

    @Test
    void reversedRangeIsSwapped() {
        IndiaDay.Range range = IndiaDay.range(LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 4));

        assertThat(range.fromInclusive()).isEqualTo(Instant.parse("2026-11-03T18:30:00Z"));
        assertThat(range.toExclusive()).isEqualTo(Instant.parse("2026-11-05T18:30:00Z"));
    }

    @Test
    void missingEndStaysOpen() {
        IndiaDay.Range range = IndiaDay.range(LocalDate.of(2026, 11, 4), null);

        assertThat(range.hasFrom()).isTrue();
        assertThat(range.hasTo()).isFalse();
    }
}
