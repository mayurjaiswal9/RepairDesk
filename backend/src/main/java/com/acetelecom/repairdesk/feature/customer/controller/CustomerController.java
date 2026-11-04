package com.acetelecom.repairdesk.feature.customer.controller;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.security.access.annotation.Secured;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.acetelecom.repairdesk.common.error.ApiException;
import com.acetelecom.repairdesk.common.error.ErrorCode;
import com.acetelecom.repairdesk.feature.company.CompanyContext;
import com.acetelecom.repairdesk.feature.job.dto.JobSummary;
import com.acetelecom.repairdesk.feature.job.repository.JobRepository;
import com.acetelecom.repairdesk.feature.request.dto.RepairRequestSummary;
import com.acetelecom.repairdesk.feature.request.repository.RepairRequestRepository;
import com.acetelecom.repairdesk.feature.customer.dto.CreateCustomerRequest;
import com.acetelecom.repairdesk.feature.customer.dto.CreateCustomerResponse;
import com.acetelecom.repairdesk.feature.customer.service.CustomerService;
import com.acetelecom.repairdesk.feature.user.domain.Role;
import com.acetelecom.repairdesk.feature.user.domain.User;
import com.acetelecom.repairdesk.feature.user.dto.UserProfile;
import com.acetelecom.repairdesk.feature.user.repository.UserRepository;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/customers")
@Secured({ "ROLE_STAFF", "ROLE_ADMIN" })
public class CustomerController {

    private final UserRepository userRepo;
    private final RepairRequestRepository requestRepo;
    private final JobRepository jobRepo;
    private final CustomerService customerService;

    public CustomerController(UserRepository userRepo,
            RepairRequestRepository requestRepo,
            JobRepository jobRepo,
            CustomerService customerService) {
        this.userRepo = userRepo;
        this.requestRepo = requestRepo;
        this.jobRepo = jobRepo;
        this.customerService = customerService;
    }

    @PostMapping
    public ResponseEntity<CreateCustomerResponse> create(@Valid @RequestBody CreateCustomerRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(customerService.createWalkIn(body));
    }

    /**
     * Search customers (CUSTOMER role, non-deleted).
     *
     * <p>A missing {@code q} is normalized to {@code ""} rather than passed
     * through as {@code null}: with a genuinely {@code null} bind parameter,
     * Hibernate/the PostgreSQL driver can't infer a concrete type for {@code :q}
     * inside the {@code concat('%', :q, '%')} expression in
     * {@link UserRepository#searchCustomers} and defaults it to {@code bytea},
     * which blows up {@code lower(...)} with a {@code SQLGrammarException}
     * (BUG-9). An empty string matches everything via the existing
     * {@code LIKE '%%'} pattern, so this is behavior-preserving.
     */
    @GetMapping
    public List<UserProfile> search(@RequestParam(required = false) String q) {
        return userRepo.searchCustomers(Role.CUSTOMER, q == null ? "" : q).stream()
                .map(UserProfile::from)
                .toList();
    }

    /** Customer profile. */
    @GetMapping("/{id}")
    public UserProfile profile(@PathVariable UUID id) {
        User user = userRepo.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Customer not found"));
        CompanyContext.requireSame(user.getCompanyId(), "Customer not found");
        if (user.getRole() != Role.CUSTOMER) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Customer not found");
        }
        int repairCount = requestRepo.findByCustomerIdOrderByUpdatedAtDesc(id).size();
        return UserProfile.from(user, repairCount);
    }

    /**
     * Customer repair history: all requests and their jobs.
     *
     * <p>{@code @Transactional} is required here because {@code open-in-view}
     * is disabled: {@code RepairRequestSummary.from} / {@code JobSummary.from}
     * touch LAZY {@code customer} and {@code deviceCategory} associations, which
     * would otherwise throw {@code LazyInitializationException} once the
     * repository call's transaction has closed (BUG-6).
     */
    @GetMapping("/{id}/history")
    @Transactional(readOnly = true)
    public CustomerHistory history(@PathVariable UUID id) {
        User user = userRepo.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Customer not found"));
        CompanyContext.requireSame(user.getCompanyId(), "Customer not found");
        if (user.getRole() != Role.CUSTOMER) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Customer not found");
        }

        var requests = requestRepo.findByCustomerIdOrderByUpdatedAtDesc(id).stream()
                .map(r -> RepairRequestSummary.from(r,
                        jobRepo.findByRequestId(r.getId()).orElse(null)))
                .toList();
        var jobs = jobRepo.findByCustomerId(id).stream()
                .map(JobSummary::from)
                .sorted(Comparator.comparing(JobSummary::updatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        return new CustomerHistory(UserProfile.from(user, requests.size()), requests, jobs);
    }

    record CustomerHistory(UserProfile customer,
            List<RepairRequestSummary> requests,
            List<JobSummary> jobs) {
    }
}
