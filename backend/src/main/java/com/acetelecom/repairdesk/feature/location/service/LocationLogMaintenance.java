package com.acetelecom.repairdesk.feature.location.service;

import java.time.Clock;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Deletes shared positions once they are older than two days. */
@Component
public class LocationLogMaintenance {

    static final String TASK = "location-log-retention";

    private static final Logger log = LoggerFactory.getLogger(LocationLogMaintenance.class);

    private final ScheduledTaskGuard guard;
    private final LocationHistoryService history;
    private final Clock clock;

    public LocationLogMaintenance(ScheduledTaskGuard guard, LocationHistoryService history, Clock clock) {
        this.guard = guard;
        this.history = history;
        this.clock = clock;
    }

    @Scheduled(cron = "0 20 2 * * *", zone = "Asia/Kolkata")
    public void deleteExpiredNightly() {
        deleteExpired();
    }

    @Scheduled(fixedDelay = 3_600_000, initialDelay = 150_000)
    public void deleteExpiredCatchUp() {
        deleteExpired();
    }

    void deleteExpired() {
        if (!guard.claim(TASK, Duration.ofMinutes(50), clock.instant())) {
            return;
        }
        int deleted = history.deleteExpired(clock.instant());
        log.info("Deleted {} technician locations older than 2 days", deleted);
    }
}
