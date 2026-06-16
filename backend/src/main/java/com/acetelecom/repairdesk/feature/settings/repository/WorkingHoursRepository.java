package com.acetelecom.repairdesk.feature.settings.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.acetelecom.repairdesk.feature.settings.domain.WorkingHours;

public interface WorkingHoursRepository extends JpaRepository<WorkingHours, UUID> {

    List<WorkingHours> findAllByOrderByDayOfWeekAsc();
}
