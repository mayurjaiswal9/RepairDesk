package com.acetelecom.repairdesk.feature.scheduling.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acetelecom.repairdesk.common.error.ApiException;
import com.acetelecom.repairdesk.common.error.ErrorCode;
import com.acetelecom.repairdesk.feature.audit.service.AuditService;
import com.acetelecom.repairdesk.feature.job.domain.Job;
import com.acetelecom.repairdesk.feature.job.domain.JobStatus;
import com.acetelecom.repairdesk.feature.job.repository.JobRepository;
import com.acetelecom.repairdesk.feature.notification.service.NotificationService;
import com.acetelecom.repairdesk.feature.scheduling.domain.Appointment;
import com.acetelecom.repairdesk.feature.scheduling.domain.AppointmentStatus;
import com.acetelecom.repairdesk.feature.scheduling.dto.AppointmentResponse;
import com.acetelecom.repairdesk.feature.scheduling.repository.AppointmentRepository;
import com.acetelecom.repairdesk.feature.settings.service.SettingsService;
import com.acetelecom.repairdesk.feature.user.domain.Role;
import com.acetelecom.repairdesk.feature.user.domain.User;
import com.acetelecom.repairdesk.feature.user.repository.UserRepository;
import com.acetelecom.repairdesk.security.AuthenticatedUser;

@Service
@Transactional
public class AppointmentService {

    private final AppointmentRepository appointmentRepo;
    private final JobRepository jobRepo;
    private final UserRepository userRepo;
    private final SettingsService settingsService;
    private final NotificationService notifService;
    private final AuditService auditService;

    public AppointmentService(AppointmentRepository appointmentRepo, JobRepository jobRepo,
            UserRepository userRepo, SettingsService settingsService,
            NotificationService notifService, AuditService auditService) {
        this.appointmentRepo = appointmentRepo;
        this.jobRepo = jobRepo;
        this.userRepo = userRepo;
        this.settingsService = settingsService;
        this.notifService = notifService;
        this.auditService = auditService;
    }

    // ── Create ─────────────────────────────────────────────────────────────

    public AppointmentResponse create(UUID jobId, UUID technicianId,
            LocalDate date, LocalTime startTime, String notes, AuthenticatedUser actor) {

        Job job = requireJob(jobId);
        assertStaffOrAdmin(actor);

        if (job.getStatus() == JobStatus.COMPLETED || job.getStatus() == JobStatus.CANCELLED) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "Cannot schedule an appointment for a job in status " + job.getStatus());
        }
        if (appointmentRepo.findActiveByJobId(jobId).isPresent()) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "Job already has an active appointment. Reschedule or cancel it first.");
        }

        User technician = userRepo.findById(technicianId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Technician not found"));
        if (technician.getRole() != Role.TECHNICIAN || !technician.isActive()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "User is not an active technician");
        }

        var settings = settingsService.getSettings();
        int slotMinutes = settings.getSlotDurationMinutes();
        LocalTime endTime = startTime.plusMinutes(slotMinutes);

        validateDateTime(date, startTime, endTime, settings.getBookingHorizonDays());

        // Overlap warning (non-blocking — just included in response notes)
        boolean overlap = hasOverlap(technicianId, date, startTime, endTime, null);

        User actor_ = userRepo.findById(actor.id())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Actor not found"));

        Appointment appt = new Appointment();
        appt.setJob(job);
        appt.setTechnician(technician);
        appt.setScheduledDate(date);
        appt.setStartTime(startTime);
        appt.setEndTime(endTime);
        appt.setAppointmentNotes(notes);
        appt.setCreatedBy(actor_);
        appointmentRepo.save(appt);

        job.setCurrentAppointmentId(appt.getId());
        if (job.getStatus() == JobStatus.ASSIGNED || job.getStatus() == JobStatus.CREATED) {
            job.setStatus(JobStatus.SCHEDULED);
        }
        if (job.getAssignedTechnician() == null) {
            job.setAssignedTechnician(technician);
        }
        jobRepo.save(job);

        notifService.send(job.getCustomer().getId(), "APPOINTMENT_SCHEDULED",
                "Your repair appointment is scheduled for " + date + " at " + startTime + ".",
                job.getReferenceNumber(), "JOB", job.getId());

        auditService.record(actor, "JOB", jobId, "APPOINTMENT_SCHEDULED",
                "Appointment " + appt.getId() + " on " + date + " at " + startTime
                + (overlap ? " [OVERLAP WARNING]" : ""));

        return AppointmentResponse.from(appt);
    }

    // ── Reschedule ─────────────────────────────────────────────────────────

    public AppointmentResponse reschedule(UUID jobId, UUID appointmentId,
            LocalDate newDate, LocalTime newStartTime, String notes, AuthenticatedUser actor) {

        assertStaffOrAdmin(actor);
        Job job = requireJob(jobId);

        Appointment old = appointmentRepo.findById(appointmentId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Appointment not found"));
        if (!old.getJob().getId().equals(jobId)) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Appointment does not belong to this job");
        }
        if (old.getStatus() == AppointmentStatus.COMPLETED
                || old.getStatus() == AppointmentStatus.CANCELLED) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "Cannot reschedule a " + old.getStatus() + " appointment");
        }

        var settings = settingsService.getSettings();
        LocalTime newEndTime = newStartTime.plusMinutes(settings.getSlotDurationMinutes());
        validateDateTime(newDate, newStartTime, newEndTime, settings.getBookingHorizonDays());

        // Cancel old
        old.setStatus(AppointmentStatus.CANCELLED);
        old.setCancellationReason("Rescheduled");
        old.setUpdatedAt(Instant.now());
        appointmentRepo.save(old);

        User actor_ = userRepo.findById(actor.id())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Actor not found"));

        Appointment newAppt = new Appointment();
        newAppt.setJob(job);
        newAppt.setTechnician(old.getTechnician());
        newAppt.setScheduledDate(newDate);
        newAppt.setStartTime(newStartTime);
        newAppt.setEndTime(newEndTime);
        newAppt.setAppointmentNotes(notes);
        newAppt.setSupersededAppointmentId(old.getId());
        newAppt.setCreatedBy(actor_);
        appointmentRepo.save(newAppt);

        job.setCurrentAppointmentId(newAppt.getId());
        if (job.getStatus() == JobStatus.IN_PROGRESS) {
            job.setStatus(JobStatus.SCHEDULED);
        }
        jobRepo.save(job);

        notifService.send(job.getCustomer().getId(), "APPOINTMENT_RESCHEDULED",
                "Your appointment has been rescheduled to " + newDate + " at " + newStartTime + ".",
                job.getReferenceNumber(), "JOB", job.getId());

        auditService.record(actor, "JOB", jobId, "APPOINTMENT_RESCHEDULED",
                "Rescheduled from " + old.getScheduledDate() + " to " + newDate);

        return AppointmentResponse.from(newAppt);
    }

    // ── Cancel ─────────────────────────────────────────────────────────────

    public void cancel(UUID jobId, UUID appointmentId, String reason, AuthenticatedUser actor) {
        assertStaffOrAdmin(actor);
        Job job = requireJob(jobId);

        Appointment appt = appointmentRepo.findById(appointmentId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Appointment not found"));
        if (!appt.getJob().getId().equals(jobId)) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Appointment does not belong to this job");
        }
        if (appt.getStatus() == AppointmentStatus.COMPLETED
                || appt.getStatus() == AppointmentStatus.CANCELLED) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "Cannot cancel a " + appt.getStatus() + " appointment");
        }
        if (reason == null || reason.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Cancellation reason is required");
        }

        appt.setStatus(AppointmentStatus.CANCELLED);
        appt.setCancellationReason(reason);
        appt.setUpdatedAt(Instant.now());
        appointmentRepo.save(appt);

        job.setCurrentAppointmentId(null);
        JobStatus revertTo = job.getAssignedTechnician() != null
                ? JobStatus.ASSIGNED : JobStatus.CREATED;
        if (job.getStatus() == JobStatus.SCHEDULED) {
            job.setStatus(revertTo);
        }
        jobRepo.save(job);

        notifService.send(job.getCustomer().getId(), "APPOINTMENT_CANCELLED",
                "Your appointment on " + appt.getScheduledDate() + " has been cancelled. " + reason,
                job.getReferenceNumber(), "JOB", job.getId());

        auditService.record(actor, "JOB", jobId, "APPOINTMENT_CANCELLED", reason);
    }

    // ── List / Schedule ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AppointmentResponse> listForRange(LocalDate from, LocalDate to) {
        return appointmentRepo.findAllInRange(from, to).stream()
                .map(AppointmentResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> mySchedule(AuthenticatedUser actor,
            LocalDate from, LocalDate to) {
        return appointmentRepo.findScheduleForTechnician(actor.id(), from, to).stream()
                .map(AppointmentResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> forJob(UUID jobId) {
        return appointmentRepo.findByJobIdAndStatusNot(jobId, AppointmentStatus.CANCELLED).stream()
                .map(AppointmentResponse::from).toList();
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private Job requireJob(UUID jobId) {
        return jobRepo.findById(jobId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Job not found"));
    }

    private void assertStaffOrAdmin(AuthenticatedUser actor) {
        String role = actor.role();
        if (!"STAFF".equals(role) && !"ADMIN".equals(role)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Staff or Admin access required");
        }
    }

    private void validateDateTime(LocalDate date, LocalTime start, LocalTime end, int horizonDays) {
        LocalDate today = LocalDate.now();
        if (date.isBefore(today)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Appointment date cannot be in the past");
        }
        if (date.isAfter(today.plusDays(horizonDays))) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Appointment date is beyond the booking horizon of " + horizonDays + " days");
        }
        if (!end.isAfter(start)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "End time must be after start time");
        }
    }

    private boolean hasOverlap(UUID techId, LocalDate date, LocalTime start, LocalTime end,
            UUID excludeId) {
        return appointmentRepo.findByTechnicianIdAndDate(techId, date).stream()
                .filter(a -> excludeId == null || !a.getId().equals(excludeId))
                .anyMatch(a -> start.isBefore(a.getEndTime()) && end.isAfter(a.getStartTime()));
    }
}
