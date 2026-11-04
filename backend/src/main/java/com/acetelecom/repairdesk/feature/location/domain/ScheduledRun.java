package com.acetelecom.repairdesk.feature.location.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "scheduled_runs")
@Getter
@Setter
@NoArgsConstructor
public class ScheduledRun {

    @Id
    @Column(name = "task_name", length = 60)
    private String taskName;

    @Column(name = "last_run_at", nullable = false)
    private Instant lastRunAt;
}
