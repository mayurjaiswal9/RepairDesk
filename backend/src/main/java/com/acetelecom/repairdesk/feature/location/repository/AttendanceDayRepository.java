package com.acetelecom.repairdesk.feature.location.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.acetelecom.repairdesk.feature.location.domain.AttendanceDay;

public interface AttendanceDayRepository extends JpaRepository<AttendanceDay, UUID> {

    Optional<AttendanceDay> findByTechnicianIdAndWorkDate(UUID technicianId, LocalDate workDate);

    List<AttendanceDay> findByWorkDateBetween(LocalDate from, LocalDate to);

    List<AttendanceDay> findByEndAtIsNull();
}
