package com.acetelecom.repairdesk.feature.location.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class SuggestionRateLimiterTest {

    @Test
    void sixtyFirstSearchInAnHourIsRefused() {
        SuggestionRateLimiter limiter = new SuggestionRateLimiter();
        UUID user = UUID.randomUUID();
        Instant start = Instant.parse("2026-10-18T10:00:00Z");
        for (int i = 0; i < SuggestionRateLimiter.LIMIT; i++) {
            assertThat(limiter.allow(user, start.plus(i, ChronoUnit.SECONDS))).isTrue();
        }
        assertThat(limiter.allow(user, start.plus(2, ChronoUnit.MINUTES))).isFalse();
        assertThat(limiter.allow(user, start.plus(61, ChronoUnit.MINUTES))).isTrue();
    }
}
