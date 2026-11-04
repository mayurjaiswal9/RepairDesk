package com.acetelecom.repairdesk.feature.location.service;

import java.time.Clock;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.acetelecom.repairdesk.feature.location.service.AttendanceService.AutoEndResult;
import com.acetelecom.repairdesk.feature.notification.service.NotificationService;

/**
 * Closes open work days at the stored India time. Does not change a job and
 * does not send SMS.
 */
@Component
public class AttendanceAutoEnd {

    private static final Logger log = LoggerFactory.getLogger(AttendanceAutoEnd.class);
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");

    private final ScheduledTaskGuard guard;
    private final AttendanceService attendance;
    private final NotificationService notifications;
    private final Clock clock;

    public AttendanceAutoEnd(ScheduledTaskGuard guard, AttendanceService attendance,
            NotificationService notifications, Clock clock) {
        this.guard = guard;
        this.attendance = attendance;
        this.notifications = notifications;
        this.clock = clock;
    }

    @Scheduled(fixedDelay = 900_000, initialDelay = 60_000)
    public void run() {
        if (!guard.claim("attendance-auto-end", Duration.ofMinutes(14), clock.instant())) {
            return;
        }
        AutoEndResult result = attendance.autoEndOpenDays();
        String when = result.autoEndTime().format(CLOCK);
        for (UUID technicianId : result.technicianIds()) {
            try {
                notifications.send(technicianId, "DAY_ENDED",
                        "Your work day ended automatically at " + when + ".",
                        null, null, null);
            } catch (RuntimeException ex) {
                log.warn("Attendance auto-end notice was not sent");
            }
        }
    }
}
