package com.acetelecom.repairdesk.feature.trip.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.acetelecom.repairdesk.feature.location.service.ScheduledTaskGuard;

/**
 * Ends trips left running for six hours, and deletes trip points past retention
 * once per night after 02:30 India time. The hourly run catches up after a cold start.
 */
@Component
public class TripMaintenance {

    static final String TIMEOUT_TASK = "trip-timeout";
    static final String RETENTION_TASK = "trip-points-retention";

    private static final Logger log = LoggerFactory.getLogger(TripMaintenance.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final LocalTime RETENTION_TIME = LocalTime.of(2, 30);

    private final ScheduledTaskGuard guard;
    private final TripService trips;
    private final Clock clock;

    public TripMaintenance(ScheduledTaskGuard guard, TripService trips, Clock clock) {
        this.guard = guard;
        this.trips = trips;
        this.clock = clock;
    }

    @Scheduled(fixedDelay = 900_000, initialDelay = 90_000)
    public void endTimedOutTrips() {
        if (!guard.claim(TIMEOUT_TASK, Duration.ofMinutes(14), clock.instant())) {
            return;
        }
        int ended = trips.endTimedOutTrips();
        if (ended > 0) {
            log.info("Ended {} trips after six hours", ended);
        }
    }

    @Scheduled(cron = "0 30 2 * * *", zone = "Asia/Kolkata")
    public void deleteExpiredPointsNightly() {
        deleteExpiredPoints();
    }

    @Scheduled(fixedDelay = 3_600_000, initialDelay = 120_000)
    public void deleteExpiredPointsCatchUp() {
        deleteExpiredPoints();
    }

    void deleteExpiredPoints() {
        Instant now = clock.instant();
        if (!guard.claim(RETENTION_TASK, Duration.between(lastRetentionTime(now), now), now)) {
            return;
        }
        int deleted = trips.deleteExpiredPoints();
        log.info("Deleted {} trip points past retention", deleted);
    }

    /** The most recent 02:30 India time at or before {@code now}. */
    static Instant lastRetentionTime(Instant now) {
        ZonedDateTime india = now.atZone(IST);
        ZonedDateTime today = india.toLocalDate().atTime(RETENTION_TIME).atZone(IST);
        return (india.isBefore(today) ? today.minusDays(1) : today).toInstant();
    }
}
