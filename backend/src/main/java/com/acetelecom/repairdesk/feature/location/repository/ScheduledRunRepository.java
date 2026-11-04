package com.acetelecom.repairdesk.feature.location.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.acetelecom.repairdesk.feature.location.domain.ScheduledRun;

public interface ScheduledRunRepository extends JpaRepository<ScheduledRun, String> {
}
