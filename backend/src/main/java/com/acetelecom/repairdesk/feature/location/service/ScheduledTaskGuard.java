package com.acetelecom.repairdesk.feature.location.service;

import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acetelecom.repairdesk.feature.location.domain.ScheduledRun;
import com.acetelecom.repairdesk.feature.location.repository.ScheduledRunRepository;

/**
 * Decides whether a scheduled task should run. After the container scales to
 * zero, the next start claims the task if the gap has elapsed.
 */
@Service
public class ScheduledTaskGuard {

    private final ScheduledRunRepository repo;

    public ScheduledTaskGuard(ScheduledRunRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public boolean claim(String taskName, Duration minGap, Instant now) {
        ScheduledRun run = repo.findById(taskName).orElseGet(ScheduledRun::new);
        if (run.getLastRunAt() != null && Duration.between(run.getLastRunAt(), now).compareTo(minGap) < 0) {
            return false;
        }
        run.setTaskName(taskName);
        run.setLastRunAt(now);
        repo.save(run);
        return true;
    }
}
