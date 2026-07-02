package com.acetelecom.repairdesk.feature.scheduling.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.acetelecom.repairdesk.feature.scheduling.domain.Appointment;
import com.acetelecom.repairdesk.feature.scheduling.domain.AppointmentStatus;

public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {

    /** All non-cancelled appointments for a job (to find the active one). */
    List<Appointment> findByJobIdAndStatusNot(UUID jobId, AppointmentStatus status);

    /** Active (SCHEDULED or IN_PROGRESS) appointment for a job. */
    @Query("SELECT a FROM Appointment a WHERE a.job.id = :jobId AND a.status IN ('SCHEDULED','IN_PROGRESS')")
    Optional<Appointment> findActiveByJobId(@Param("jobId") UUID jobId);

    /** All appointments for a technician on a given date (conflict checks). */
    @Query("SELECT a FROM Appointment a WHERE a.technician.id = :techId AND a.scheduledDate = :date AND a.status NOT IN ('CANCELLED')")
    List<Appointment> findByTechnicianIdAndDate(@Param("techId") UUID techId,
            @Param("date") LocalDate date);

    /** Technician's own schedule in a date range, ordered by date/time. */
    @Query("SELECT a FROM Appointment a WHERE a.technician.id = :techId "
            + "AND a.scheduledDate BETWEEN :from AND :to "
            + "AND a.status NOT IN ('CANCELLED') "
            + "ORDER BY a.scheduledDate, a.startTime")
    List<Appointment> findScheduleForTechnician(@Param("techId") UUID techId,
            @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Staff/admin: all appointments in a date range for calendar. */
    @Query("SELECT a FROM Appointment a WHERE a.scheduledDate BETWEEN :from AND :to "
            + "AND a.status NOT IN ('CANCELLED') ORDER BY a.scheduledDate, a.startTime")
    List<Appointment> findAllInRange(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
