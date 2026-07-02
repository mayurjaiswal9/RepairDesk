package com.acetelecom.repairdesk.feature.scheduling.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.acetelecom.repairdesk.common.error.ApiException;
import com.acetelecom.repairdesk.feature.audit.service.AuditService;
import com.acetelecom.repairdesk.feature.job.domain.Job;
import com.acetelecom.repairdesk.feature.job.domain.JobStatus;
import com.acetelecom.repairdesk.feature.job.repository.JobRepository;
import com.acetelecom.repairdesk.feature.notification.service.NotificationService;
import com.acetelecom.repairdesk.feature.request.domain.Priority;
import com.acetelecom.repairdesk.feature.scheduling.domain.Appointment;
import com.acetelecom.repairdesk.feature.scheduling.domain.AppointmentStatus;
import com.acetelecom.repairdesk.feature.scheduling.repository.AppointmentRepository;
import com.acetelecom.repairdesk.feature.settings.domain.BusinessSettings;
import com.acetelecom.repairdesk.feature.settings.service.SettingsService;
import com.acetelecom.repairdesk.feature.user.domain.Role;
import com.acetelecom.repairdesk.feature.user.domain.User;
import com.acetelecom.repairdesk.feature.user.repository.UserRepository;
import com.acetelecom.repairdesk.security.AuthenticatedUser;

/** TEST-004: Scheduling validation unit tests. */
@ExtendWith(MockitoExtension.class)
class AppointmentServiceTest {

    @Mock private AppointmentRepository appointmentRepo;
    @Mock private JobRepository jobRepo;
    @Mock private UserRepository userRepo;
    @Mock private SettingsService settingsService;
    @Mock private NotificationService notifService;
    @Mock private AuditService auditService;

    private AppointmentService appointmentService;
    private AuthenticatedUser staffActor;

    @BeforeEach
    void setUp() {
        appointmentService = new AppointmentService(appointmentRepo, jobRepo,
                userRepo, settingsService, notifService, auditService);
        staffActor = new AuthenticatedUser(UUID.randomUUID(), "staff1", "STAFF");
    }

    // ── create: date validation ───────────────────────────────────────────────

    @Test
    void create_pastDate_throwsValidation() {
        UUID jobId = UUID.randomUUID();
        UUID techId = UUID.randomUUID();
        Job job = buildJob(jobId, JobStatus.ASSIGNED);
        User tech = buildTechnician(techId);
        setupSettings(60, 30);

        when(jobRepo.findById(jobId)).thenReturn(Optional.of(job));
        when(appointmentRepo.findActiveByJobId(jobId)).thenReturn(Optional.empty());
        when(userRepo.findById(techId)).thenReturn(Optional.of(tech));

        LocalDate yesterday = LocalDate.now().minusDays(1);
        assertThatThrownBy(() -> appointmentService.create(
                jobId, techId, yesterday, LocalTime.of(10, 0), null, staffActor))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("past");
    }

    @Test
    void create_beyondHorizon_throwsValidation() {
        UUID jobId = UUID.randomUUID();
        UUID techId = UUID.randomUUID();
        Job job = buildJob(jobId, JobStatus.ASSIGNED);
        User tech = buildTechnician(techId);
        setupSettings(14, 30); // horizon = 14 days

        when(jobRepo.findById(jobId)).thenReturn(Optional.of(job));
        when(appointmentRepo.findActiveByJobId(jobId)).thenReturn(Optional.empty());
        when(userRepo.findById(techId)).thenReturn(Optional.of(tech));

        LocalDate tooFar = LocalDate.now().plusDays(15); // > 14-day horizon
        assertThatThrownBy(() -> appointmentService.create(
                jobId, techId, tooFar, LocalTime.of(10, 0), null, staffActor))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("horizon");
    }

    @Test
    void create_completedJob_throwsConflict() {
        UUID jobId = UUID.randomUUID();
        Job job = buildJob(jobId, JobStatus.COMPLETED);
        when(jobRepo.findById(jobId)).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> appointmentService.create(
                jobId, UUID.randomUUID(), LocalDate.now().plusDays(1),
                LocalTime.of(10, 0), null, staffActor))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("COMPLETED");
    }

    @Test
    void create_existingActiveAppointment_throwsConflict() {
        UUID jobId = UUID.randomUUID();
        UUID techId = UUID.randomUUID();
        Job job = buildJob(jobId, JobStatus.ASSIGNED);
        User tech = buildTechnician(techId);
        when(jobRepo.findById(jobId)).thenReturn(Optional.of(job));
        when(appointmentRepo.findActiveByJobId(jobId))
                .thenReturn(Optional.of(new Appointment()));

        assertThatThrownBy(() -> appointmentService.create(
                jobId, techId, LocalDate.now().plusDays(1),
                LocalTime.of(10, 0), null, staffActor))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("active appointment");
    }

    @Test
    void create_notActiveTechnician_throwsValidation() {
        UUID jobId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Job job = buildJob(jobId, JobStatus.ASSIGNED);
        User staff = new User();
        staff.setId(userId);
        staff.setRole(Role.STAFF);
        staff.setStatus(com.acetelecom.repairdesk.feature.user.domain.AccountStatus.ACTIVE);

        when(jobRepo.findById(jobId)).thenReturn(Optional.of(job));
        when(appointmentRepo.findActiveByJobId(jobId)).thenReturn(Optional.empty());
        when(userRepo.findById(userId)).thenReturn(Optional.of(staff));

        assertThatThrownBy(() -> appointmentService.create(
                jobId, userId, LocalDate.now().plusDays(1),
                LocalTime.of(10, 0), null, staffActor))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("technician");
    }

    @Test
    void create_byCustomer_throwsForbidden() {
        UUID jobId = UUID.randomUUID();
        Job job = buildJob(jobId, JobStatus.ASSIGNED);
        when(jobRepo.findById(jobId)).thenReturn(Optional.of(job));
        AuthenticatedUser customer = new AuthenticatedUser(
                UUID.randomUUID(), "cust", "CUSTOMER");

        assertThatThrownBy(() -> appointmentService.create(
                jobId, UUID.randomUUID(), LocalDate.now().plusDays(1),
                LocalTime.of(10, 0), null, customer))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Staff or Admin");
    }

    // ── cancel ────────────────────────────────────────────────────────────────

    @Test
    void cancel_withoutReason_throwsValidation() {
        UUID jobId = UUID.randomUUID();
        UUID apptId = UUID.randomUUID();
        Job job = buildJob(jobId, JobStatus.SCHEDULED);
        Appointment appt = new Appointment();
        appt.setId(apptId);
        appt.setJob(job);

        when(jobRepo.findById(jobId)).thenReturn(Optional.of(job));
        when(appointmentRepo.findById(apptId)).thenReturn(Optional.of(appt));

        assertThatThrownBy(() -> appointmentService.cancel(
                jobId, apptId, "  ", staffActor))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("reason");
    }

    @Test
    void cancel_alreadyCancelled_throwsConflict() {
        UUID jobId = UUID.randomUUID();
        UUID apptId = UUID.randomUUID();
        Job job = buildJob(jobId, JobStatus.SCHEDULED);
        Appointment appt = new Appointment();
        appt.setId(apptId);
        appt.setJob(job);
        appt.setStatus(AppointmentStatus.CANCELLED);

        when(jobRepo.findById(jobId)).thenReturn(Optional.of(job));
        when(appointmentRepo.findById(apptId)).thenReturn(Optional.of(appt));

        assertThatThrownBy(() -> appointmentService.cancel(
                jobId, apptId, "reason", staffActor))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("CANCELLED");
    }

    // ── Builders ──────────────────────────────────────────────────────────────

    private Job buildJob(UUID id, JobStatus status) {
        Job job = new Job();
        job.setId(id);
        job.setStatus(status);
        job.setReferenceNumber("RD-2026000001");
        job.setPriority(Priority.NORMAL);
        User customer = new User();
        customer.setId(UUID.randomUUID());
        job.setCustomer(customer);
        job.setDeviceCategory(new com.acetelecom.repairdesk.feature.devicecategory.domain.DeviceCategory());
        return job;
    }

    private User buildTechnician(UUID id) {
        User tech = new User();
        tech.setId(id);
        tech.setRole(Role.TECHNICIAN);
        tech.setStatus(com.acetelecom.repairdesk.feature.user.domain.AccountStatus.ACTIVE);
        tech.setName("Tech One");
        return tech;
    }

    private void setupSettings(int bookingHorizonDays, int slotMinutes) {
        BusinessSettings settings = new BusinessSettings();
        settings.setBookingHorizonDays(bookingHorizonDays);
        settings.setSlotDurationMinutes(slotMinutes);
        when(settingsService.getSettings()).thenReturn(settings);
    }
}
