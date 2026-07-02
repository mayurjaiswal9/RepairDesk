package com.acetelecom.repairdesk.feature.request.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import com.acetelecom.repairdesk.common.error.ApiException;
import com.acetelecom.repairdesk.feature.audit.service.AuditService;
import com.acetelecom.repairdesk.feature.devicecategory.domain.DeviceCategory;
import com.acetelecom.repairdesk.feature.devicecategory.repository.DeviceCategoryRepository;
import com.acetelecom.repairdesk.feature.request.domain.Priority;
import com.acetelecom.repairdesk.feature.request.domain.RepairRequest;
import com.acetelecom.repairdesk.feature.request.domain.RequestStatus;
import com.acetelecom.repairdesk.feature.request.dto.CreateRequestDto;
import com.acetelecom.repairdesk.feature.request.repository.RepairRequestRepository;
import com.acetelecom.repairdesk.feature.user.domain.Role;
import com.acetelecom.repairdesk.feature.user.domain.User;
import com.acetelecom.repairdesk.feature.user.repository.UserRepository;
import com.acetelecom.repairdesk.security.AuthenticatedUser;

/** TEST-002: Repair request lifecycle. */
@ExtendWith(MockitoExtension.class)
class RepairRequestServiceTest {

    @Mock private RepairRequestRepository requestRepo;
    @Mock private DeviceCategoryRepository categoryRepo;
    @Mock private UserRepository userRepo;
    @Mock private JdbcTemplate jdbc;
    @Mock private AuditService auditService;

    private RepairRequestService service;
    private AuthenticatedUser customerActor;
    private AuthenticatedUser staffActor;

    @BeforeEach
    void setUp() {
        service = new RepairRequestService(requestRepo, categoryRepo,
                userRepo, jdbc, auditService);

        UUID customerId = UUID.randomUUID();
        customerActor = new AuthenticatedUser(customerId, "cust1", "CUSTOMER");
        staffActor = new AuthenticatedUser(UUID.randomUUID(), "staff1", "STAFF");
    }

    // ── cancel ────────────────────────────────────────────────────────────────

    @Test
    void cancel_ownPendingRequest_succeeds() {
        UUID reqId = UUID.randomUUID();
        RepairRequest req = buildRequest(reqId, customerActor.id(),
                RequestStatus.PENDING_REVIEW);
        when(requestRepo.findById(reqId)).thenReturn(Optional.of(req));

        service.cancel(reqId, customerActor);

        assertThat(req.getStatus()).isEqualTo(RequestStatus.CANCELLED);
    }

    @Test
    void cancel_notOwner_throwsForbidden() {
        UUID reqId = UUID.randomUUID();
        RepairRequest req = buildRequest(reqId, UUID.randomUUID(),
                RequestStatus.PENDING_REVIEW);
        when(requestRepo.findById(reqId)).thenReturn(Optional.of(req));

        assertThatThrownBy(() -> service.cancel(reqId, customerActor))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Access denied");
    }

    @Test
    void cancel_approvedRequest_throwsConflict() {
        UUID reqId = UUID.randomUUID();
        RepairRequest req = buildRequest(reqId, customerActor.id(),
                RequestStatus.APPROVED);
        when(requestRepo.findById(reqId)).thenReturn(Optional.of(req));

        assertThatThrownBy(() -> service.cancel(reqId, customerActor))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("cancelled");
    }

    // ── setPriority (RV-005) ─────────────────────────────────────────────────

    @Test
    void setPriority_byStaff_updatesPriority() {
        UUID reqId = UUID.randomUUID();
        RepairRequest req = buildRequest(reqId, UUID.randomUUID(),
                RequestStatus.PENDING_REVIEW);
        req.setPriority(Priority.NORMAL);
        when(requestRepo.findById(reqId)).thenReturn(Optional.of(req));
        when(requestRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.setPriority(reqId, "HIGH", staffActor);

        assertThat(req.getPriority()).isEqualTo(Priority.HIGH);
    }

    @Test
    void setPriority_unknownValue_throwsValidationError() {
        UUID reqId = UUID.randomUUID();
        RepairRequest req = buildRequest(reqId, UUID.randomUUID(),
                RequestStatus.PENDING_REVIEW);
        when(requestRepo.findById(reqId)).thenReturn(Optional.of(req));

        assertThatThrownBy(() -> service.setPriority(reqId, "SUPER_URGENT", staffActor))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Unknown priority");
    }

    @Test
    void setPriority_byCustomer_throwsForbidden() {
        UUID reqId = UUID.randomUUID();
        RepairRequest req = buildRequest(reqId, customerActor.id(),
                RequestStatus.PENDING_REVIEW);

        assertThatThrownBy(() -> service.setPriority(reqId, "HIGH", customerActor))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Staff or Admin");
    }

    // ── Builders ──────────────────────────────────────────────────────────────

    private RepairRequest buildRequest(UUID id, UUID customerId, RequestStatus status) {
        RepairRequest req = new RepairRequest();
        req.setId(id);
        req.setStatus(status);
        req.setReferenceNumber("RD-2026000001");
        req.setPriority(Priority.NORMAL);
        User customer = new User();
        customer.setId(customerId);
        req.setCustomer(customer);
        req.setDeviceCategory(new DeviceCategory());
        req.setContactName("Test User");
        req.setContactMobile("9999999999");
        req.setIssueDescription("Cracked screen");
        req.setPreferredDate(LocalDate.now().plusDays(2));
        req.setPreferredTime(LocalTime.of(10, 0));
        return req;
    }
}
