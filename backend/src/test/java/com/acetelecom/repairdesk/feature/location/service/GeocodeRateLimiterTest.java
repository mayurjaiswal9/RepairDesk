package com.acetelecom.repairdesk.feature.location.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class GeocodeRateLimiterTest {

    @Test
    void thirtyFirstLookupInAnHourIsRefused() {
        GeocodeRateLimiter limiter = new GeocodeRateLimiter();
        UUID user = UUID.randomUUID();
        Instant start = Instant.parse("2026-10-17T10:00:00Z");
        for (int i = 0; i < GeocodeRateLimiter.LIMIT; i++) {
            assertThat(limiter.allow(user, start.plus(i, ChronoUnit.MINUTES))).isTrue();
        }
        assertThat(limiter.allow(user, start.plus(31, ChronoUnit.MINUTES))).isFalse();
        assertThat(limiter.allow(user, start.plus(61, ChronoUnit.MINUTES))).isTrue();
    }
}
