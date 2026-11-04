package com.acetelecom.repairdesk.feature.location.service;

import java.time.Clock;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Confirms the scheduler is alive and can catch up after a cold start. */
@Component
public class SchedulerHeartbeat {

    private static final Logger log = LoggerFactory.getLogger(SchedulerHeartbeat.class);

    private final ScheduledTaskGuard guard;
    private final Clock clock;

    public SchedulerHeartbeat(ScheduledTaskGuard guard, Clock clock) {
        this.guard = guard;
        this.clock = clock;
    }

    @Scheduled(fixedDelay = 3_600_000, initialDelay = 60_000)
    public void heartbeat() {
        if (guard.claim("scheduler-heartbeat", Duration.ofMinutes(50), clock.instant())) {
            log.info("Scheduler heartbeat");
        }
    }
}
