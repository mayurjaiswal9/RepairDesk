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
import com.acetelecom.repairdesk.feature.notification.domain.NotificationEvent;
import com.acetelecom.repairdesk.feature.notification.service.NotificationContext;
import com.acetelecom.repairdesk.feature.notification.service.NotificationDispatcher;

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
    private final NotificationDispatcher notifications;
    private final Clock clock;

    public AttendanceAutoEnd(ScheduledTaskGuard guard, AttendanceService attendance,
            NotificationDispatcher notifications, Clock clock) {
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
                notifications.publish(NotificationEvent.DAY_ENDED, NotificationContext.create()
                        .technician(technicianId)
                        .when(when));
            } catch (RuntimeException ex) {
                log.warn("Attendance auto-end notice was not sent");
            }
        }
    }
}
