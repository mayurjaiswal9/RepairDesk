package com.acetelecom.repairdesk.feature.location.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.acetelecom.repairdesk.feature.location.domain.ScheduledRun;
import com.acetelecom.repairdesk.feature.location.repository.ScheduledRunRepository;

@ExtendWith(MockitoExtension.class)
class ScheduledTaskGuardTest {

    @Mock private ScheduledRunRepository repo;

    @Test
    void secondClaimInsideTheGapIsSkipped() {
        ScheduledRun stored = new ScheduledRun();
        when(repo.findById("scheduler-heartbeat")).thenReturn(Optional.empty(), Optional.of(stored));
        when(repo.save(any())).thenAnswer(inv -> {
            ScheduledRun saved = inv.getArgument(0);
            stored.setTaskName(saved.getTaskName());
            stored.setLastRunAt(saved.getLastRunAt());
            return stored;
        });
        ScheduledTaskGuard guard = new ScheduledTaskGuard(repo);
        Instant now = Instant.parse("2026-10-17T10:00:00Z");

        assertThat(guard.claim("scheduler-heartbeat", Duration.ofMinutes(50), now)).isTrue();
        assertThat(guard.claim("scheduler-heartbeat", Duration.ofMinutes(50), now.plusSeconds(60))).isFalse();
        assertThat(guard.claim("scheduler-heartbeat", Duration.ofMinutes(50), now.plus(Duration.ofMinutes(51)))).isTrue();
    }
}
