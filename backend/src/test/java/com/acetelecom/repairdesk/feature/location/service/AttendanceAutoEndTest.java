package com.acetelecom.repairdesk.feature.location.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.acetelecom.repairdesk.feature.location.service.AttendanceService.AutoEndResult;
import com.acetelecom.repairdesk.feature.notification.service.NotificationService;

@ExtendWith(MockitoExtension.class)
class AttendanceAutoEndTest {

    @Mock private ScheduledTaskGuard guard;
    @Mock private AttendanceService attendance;
    @Mock private NotificationService notifications;

    @Test
    void skippedClaimDoesNotCloseDaysOrNotify() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-18T13:30:00Z"), ZoneOffset.UTC);
        when(guard.claim(eq("attendance-auto-end"), any(), eq(clock.instant()))).thenReturn(false);
        AttendanceAutoEnd job = new AttendanceAutoEnd(guard, attendance, notifications, clock);

        job.run();

        verify(attendance, never()).autoEndOpenDays();
        verify(notifications, never()).send(any(), any(), any(), any(), any(), any());
    }

    @Test
    void claimedRunNotifiesEachClosedTechnicianAndDoesNotMentionAJob() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-18T13:30:00Z"), ZoneOffset.UTC);
        when(guard.claim(eq("attendance-auto-end"), any(), eq(clock.instant()))).thenReturn(true);
        UUID technicianId = UUID.randomUUID();
        when(attendance.autoEndOpenDays())
                .thenReturn(new AutoEndResult(LocalTime.of(19, 0), List.of(technicianId)));
        AttendanceAutoEnd job = new AttendanceAutoEnd(guard, attendance, notifications, clock);

        job.run();

        verify(notifications).send(technicianId, "DAY_ENDED",
                "Your work day ended automatically at 19:00.", null, null, null);
        verify(notifications, never()).send(any(), any(), any(), any(), eq("JOB"), any());
    }

    @Test
    void aFailedNoticeDoesNotStopTheNextTechnician() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-18T13:30:00Z"), ZoneOffset.UTC);
        when(guard.claim(eq("attendance-auto-end"), any(), eq(clock.instant()))).thenReturn(true);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(attendance.autoEndOpenDays())
                .thenReturn(new AutoEndResult(LocalTime.of(19, 0), List.of(first, second)));
        doThrow(new IllegalStateException("inbox down"))
                .when(notifications).send(eq(first), any(), any(), isNull(), isNull(), isNull());
        AttendanceAutoEnd job = new AttendanceAutoEnd(guard, attendance, notifications, clock);

        job.run();

        verify(notifications).send(eq(second), eq("DAY_ENDED"), any(), isNull(), isNull(), isNull());
    }
}
