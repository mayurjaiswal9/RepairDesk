package com.acetelecom.repairdesk.feature.job.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import com.acetelecom.repairdesk.common.error.ApiException;
import com.acetelecom.repairdesk.feature.audit.service.AuditService;
import com.acetelecom.repairdesk.feature.devicecategory.domain.DeviceCategory;
import com.acetelecom.repairdesk.feature.job.domain.Job;
import com.acetelecom.repairdesk.feature.job.domain.JobStatus;
import com.acetelecom.repairdesk.feature.job.dto.JobResponse;
import com.acetelecom.repairdesk.feature.job.dto.OpenJobCarryoverResponse;
import com.acetelecom.repairdesk.feature.job.repository.JobRepository;
import com.acetelecom.repairdesk.feature.location.domain.PinSource;
import com.acetelecom.repairdesk.feature.location.domain.ServiceLocation;
import com.acetelecom.repairdesk.feature.location.dto.ServiceLocationDto;
import com.acetelecom.repairdesk.feature.location.service.MapsService;
import com.acetelecom.repairdesk.feature.location.service.ServiceLocationApplier;
import com.acetelecom.repairdesk.feature.notification.service.NotificationService;
import com.acetelecom.repairdesk.feature.trip.service.TripService;
import com.acetelecom.repairdesk.feature.request.domain.Priority;
import com.acetelecom.repairdesk.feature.request.domain.RepairRequest;
import com.acetelecom.repairdesk.feature.request.domain.RequestStatus;
import com.acetelecom.repairdesk.feature.request.repository.RepairRequestRepository;
import com.acetelecom.repairdesk.feature.user.domain.Role;
import com.acetelecom.repairdesk.feature.user.domain.User;
import com.acetelecom.repairdesk.feature.user.repository.UserRepository;
import com.acetelecom.repairdesk.security.AuthenticatedUser;

import java.util.List;

/** TEST-002 / TEST-003: Request lifecycle and job lifecycle unit tests. */
@ExtendWith(MockitoExtension.class)
class JobServiceTest {

    @Mock
    private JobRepository jobRepo;
    @Mock
    private RepairRequestRepository requestRepo;
    @Mock
    private UserRepository userRepo;
    @Mock
    private NotificationService notifService;
    @Mock
    private AuditService auditService;
    @Mock
    private MapsService mapsService;
    @Mock
    private TripService tripService;

    private JobService jobService;
    private ServiceLocationApplier locationApplier;

    private AuthenticatedUser staffActor;
    private AuthenticatedUser techActor;

    @BeforeEach
    void setUp() {
        locationApplier = new ServiceLocationApplier(mapsService);
        jobService = new JobService(jobRepo, requestRepo, userRepo,
                notifService, auditService, locationApplier, tripService);

        UUID staffId = UUID.randomUUID();
        staffActor = new AuthenticatedUser(staffId, "staff1", "STAFF");

        UUID techId = UUID.randomUUID();
        techActor = new AuthenticatedUser(techId, "tech1", "TECHNICIAN");
    }

    // ── approve ──────────────────────────────────────────────────────────────

    @Test
    void approve_pendingRequest_createsJobAndNotifies() {
        UUID reqId = UUID.randomUUID();
        RepairRequest req = buildRequest(reqId, RequestStatus.PENDING_REVIEW);
        when(requestRepo.findById(reqId)).thenReturn(Optional.of(req));
        when(jobRepo.findByRequestId(reqId)).thenReturn(Optional.empty());
        when(jobRepo.save(any())).thenAnswer(inv -> {
            Job j = inv.getArgument(0);
            j.setId(UUID.randomUUID());
            return j;
        });

        JobResponse result = jobService.approve(reqId, staffActor);

        assertThat(result).isNotNull();
        assertThat(req.getStatus()).isEqualTo(RequestStatus.APPROVED);
        verify(notifService).send(any(), eq("JOB_APPROVED"), any(), any(), any(), any());
        verify(auditService).record(eq(staffActor), eq("REQUEST"), eq(reqId),
                eq("APPROVED"), any());
    }

    @Test
    void approve_copiesServiceLocationOntoTheJob() {
        UUID reqId = UUID.randomUUID();
        RepairRequest req = buildRequest(reqId, RequestStatus.PENDING_REVIEW);
        ServiceLocation loc = new ServiceLocation();
        loc.setCity("Mumbai");
        loc.setLatitude(19.14);
        loc.setLongitude(72.82);
        loc.setPinSource(PinSource.MANUAL_PIN);
        req.setServiceLocation(loc);
        when(requestRepo.findById(reqId)).thenReturn(Optional.of(req));
        when(jobRepo.findByRequestId(reqId)).thenReturn(Optional.empty());
        when(jobRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        jobService.approve(reqId, staffActor);

        ArgumentCaptor<Job> saved = ArgumentCaptor.forClass(Job.class);
        verify(jobRepo).save(saved.capture());
        assertThat(saved.getValue().getServiceLocation()).isNotSameAs(loc);
        assertThat(saved.getValue().getServiceLocation().getCity()).isEqualTo("Mumbai");
        assertThat(saved.getValue().getServiceLocation().getLatitude()).isEqualTo(19.14);
        assertThat(saved.getValue().getServiceLocation().getPinSource()).isEqualTo(PinSource.MANUAL_PIN);
    }

    @Test
    void updateServiceLocation_byTechnician_isForbidden() {
        ServiceLocationDto dto = new ServiceLocationDto(
                "Lokhandwala", null, "Mumbai", "400053", 19.14, 72.82, null);

        assertThatThrownBy(() -> jobService.updateServiceLocation(UUID.randomUUID(), dto, techActor))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Staff or Admin");
    }

    @Test
    void updateServiceLocation_withPin_keepsCoordinatesAndDoesNotGeocode() {
        UUID jobId = UUID.randomUUID();
        Job job = buildJob(jobId, JobStatus.ASSIGNED);
        when(jobRepo.findById(jobId)).thenReturn(Optional.of(job));
        when(jobRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        ServiceLocationDto dto = new ServiceLocationDto(
                "Lokhandwala", null, "Mumbai", "400053", 19.14, 72.82, null);

        JobResponse result = jobService.updateServiceLocation(jobId, dto, staffActor);

        assertThat(result.serviceLocation().city()).isEqualTo("Mumbai");
        assertThat(result.serviceLocation().latitude()).isEqualTo(19.14);
        assertThat(result.serviceLocation().pinSource()).isEqualTo("MANUAL_PIN");
        verify(mapsService, never()).geocode(any());
    }

    @Test
    void approve_alreadyApprovedRequest_throwsConflict() {
        UUID reqId = UUID.randomUUID();
        RepairRequest req = buildRequest(reqId, RequestStatus.APPROVED);
        when(requestRepo.findById(reqId)).thenReturn(Optional.of(req));

        assertThatThrownBy(() -> jobService.approve(reqId, staffActor))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("approved");
    }

    @Test
    void approve_whenJobAlreadyExists_throwsConflict() {
        UUID reqId = UUID.randomUUID();
        RepairRequest req = buildRequest(reqId, RequestStatus.PENDING_REVIEW);
        when(requestRepo.findById(reqId)).thenReturn(Optional.of(req));
        when(jobRepo.findByRequestId(reqId)).thenReturn(Optional.of(new Job()));

        assertThatThrownBy(() -> jobService.approve(reqId, staffActor))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Job already exists");
    }

    // ── reject ───────────────────────────────────────────────────────────────

    @Test
    void reject_pendingRequest_setsStatusAndNotifies() {
        UUID reqId = UUID.randomUUID();
        RepairRequest req = buildRequest(reqId, RequestStatus.PENDING_REVIEW);
        when(requestRepo.findById(reqId)).thenReturn(Optional.of(req));

        jobService.reject(reqId, "CANNOT_REPAIR", "Sorry, out of scope", staffActor);

        assertThat(req.getStatus()).isEqualTo(RequestStatus.REJECTED);
        assertThat(req.getRejectionReasonCode()).isEqualTo("CANNOT_REPAIR");
        verify(notifService).send(any(), eq("REQUEST_REJECTED"), any(), any(), any(), any());
    }

    @Test
    void reject_withoutReasonCode_throwsValidationError() {
        UUID reqId = UUID.randomUUID();
        RepairRequest req = buildRequest(reqId, RequestStatus.PENDING_REVIEW);
        when(requestRepo.findById(reqId)).thenReturn(Optional.of(req));

        assertThatThrownBy(() -> jobService.reject(reqId, "  ", null, staffActor))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("reason code");
    }

    // ── assign ────────────────────────────────────────────────────────────────

    @Test
    void assign_createdJob_setsTechnicianAndStatusAssigned() {
        UUID jobId = UUID.randomUUID();
        UUID techId = techActor.id();
        Job job = buildJob(jobId, JobStatus.CREATED);
        User tech = new User();
        tech.setId(techId);
        tech.setRole(Role.TECHNICIAN);
        tech.setName("Tech1");
        when(jobRepo.findById(jobId)).thenReturn(Optional.of(job));
        when(userRepo.findById(techId)).thenReturn(Optional.of(tech));
        when(jobRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        JobResponse result = jobService.assign(jobId, techId, staffActor);

        assertThat(job.getStatus()).isEqualTo(JobStatus.ASSIGNED);
        assertThat(job.getAssignedTechnician()).isEqualTo(tech);
    }

    @Test
    void assign_nonTechnicianUser_throwsValidationError() {
        UUID jobId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        Job job = buildJob(jobId, JobStatus.CREATED);
        User staffUser = new User();
        staffUser.setId(staffId);
        staffUser.setRole(Role.STAFF);
        when(jobRepo.findById(jobId)).thenReturn(Optional.of(job));
        when(userRepo.findById(staffId)).thenReturn(Optional.of(staffUser));

        assertThatThrownBy(() -> jobService.assign(jobId, staffId, staffActor))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("technician");
    }

    // ── status transitions ────────────────────────────────────────────────────

    @Test
    void start_assignedJob_transitionsToInProgress() {
        UUID jobId = UUID.randomUUID();
        Job job = buildJob(jobId, JobStatus.ASSIGNED);
        User tech = new User();
        tech.setId(techActor.id());
        job.setAssignedTechnician(tech);
        when(jobRepo.findById(jobId)).thenReturn(Optional.of(job));
        when(jobRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        jobService.start(jobId, techActor);

        assertThat(job.getStatus()).isEqualTo(JobStatus.IN_PROGRESS);
    }

    @Test
    void complete_inProgressJob_requiresResolutionSummary() {
        UUID jobId = UUID.randomUUID();
        Job job = buildJob(jobId, JobStatus.IN_PROGRESS);
        User tech = new User();
        tech.setId(techActor.id());
        job.setAssignedTechnician(tech);
        when(jobRepo.findById(jobId)).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> jobService.complete(jobId, "  ", techActor))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("summary");
    }

    @Test
    void complete_withSummary_transitionsToCompleted() {
        UUID jobId = UUID.randomUUID();
        Job job = buildJob(jobId, JobStatus.IN_PROGRESS);
        User tech = new User();
        tech.setId(techActor.id());
        job.setAssignedTechnician(tech);
        when(jobRepo.findById(jobId)).thenReturn(Optional.of(job));
        when(jobRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        jobService.complete(jobId, "Replaced screen", techActor);

        assertThat(job.getStatus()).isEqualTo(JobStatus.COMPLETED);
        assertThat(job.getResolutionSummary()).isEqualTo("Replaced screen");
    }

    @Test
    void cancel_activeJob_requiresReason() {
        UUID jobId = UUID.randomUUID();
        Job job = buildJob(jobId, JobStatus.IN_PROGRESS);
        when(jobRepo.findById(jobId)).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> jobService.cancel(jobId, "", staffActor))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("reason");
    }

    @Test
    void cancel_withReason_transitionsToCancelled() {
        UUID jobId = UUID.randomUUID();
        Job job = buildJob(jobId, JobStatus.ASSIGNED);
        when(jobRepo.findById(jobId)).thenReturn(Optional.of(job));
        when(jobRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        jobService.cancel(jobId, "Customer changed mind", staffActor);

        assertThat(job.getStatus()).isEqualTo(JobStatus.CANCELLED);
    }

    // ── Builders ──────────────────────────────────────────────────────────────

    @Test
    void oneOpenJobFromAPreviousDayIsListedOnThatIndiaDate() {
        when(jobRepo.findOpenCreatedBefore(any(), isNull())).thenReturn(List.of(
                Instant.parse("2026-11-03T19:00:00Z")));

        OpenJobCarryoverResponse result = jobService.openFromPreviousDays(staffActor);

        assertThat(result.days()).containsExactly(
                new OpenJobCarryoverResponse.Day(LocalDate.of(2026, 11, 4), 1));
    }

    @Test
    void openJobsAreGroupedByIndiaDateAndTechniciansSeeOnlyTheirOwn() {
        when(jobRepo.findOpenCreatedBefore(any(), eq(techActor.id()))).thenReturn(List.of(
                Instant.parse("2026-11-01T20:00:00Z"),
                Instant.parse("2026-11-03T18:30:00Z"),
                Instant.parse("2026-11-04T18:29:00Z")));

        OpenJobCarryoverResponse result = jobService.openFromPreviousDays(techActor);

        assertThat(result.days()).containsExactly(
                new OpenJobCarryoverResponse.Day(LocalDate.of(2026, 11, 2), 1),
                new OpenJobCarryoverResponse.Day(LocalDate.of(2026, 11, 4), 2));
    }

    private RepairRequest buildRequest(UUID id, RequestStatus status) {
        RepairRequest req = new RepairRequest();
        req.setId(id);
        req.setStatus(status);
        req.setReferenceNumber("RD-2026000001");
        req.setPriority(Priority.NORMAL);
        User customer = new User();
        customer.setId(UUID.randomUUID());
        req.setCustomer(customer);
        req.setDeviceCategory(new DeviceCategory());
        req.setContactName("Test User");
        req.setContactMobile("9999999999");
        req.setIssueDescription("Screen cracked");
        return req;
    }

    private Job buildJob(UUID id, JobStatus status) {
        Job job = new Job();
        job.setId(id);
        job.setStatus(status);
        job.setReferenceNumber("RD-2026000001");
        User customer = new User();
        customer.setId(UUID.randomUUID());
        job.setCustomer(customer);
        job.setDeviceCategory(new DeviceCategory());
        job.setPriority(Priority.NORMAL);
        // Audit calls read job.getRequest().getId() — set a stub request
        RepairRequest req = new RepairRequest();
        req.setId(UUID.randomUUID());
        req.setReferenceNumber("RD-2026000001");
        req.setCustomer(customer);
        req.setDeviceCategory(new DeviceCategory());
        req.setContactName("Test");
        req.setContactMobile("9999999999");
        req.setIssueDescription("issue");
        req.setPriority(Priority.NORMAL);
        job.setRequest(req);
        return job;
    }
}
