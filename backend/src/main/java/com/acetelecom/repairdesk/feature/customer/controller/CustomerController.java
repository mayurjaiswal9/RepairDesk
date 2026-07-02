package com.acetelecom.repairdesk.feature.customer.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.acetelecom.repairdesk.common.error.ApiException;
import com.acetelecom.repairdesk.common.error.ErrorCode;
import com.acetelecom.repairdesk.feature.job.dto.JobSummary;
import com.acetelecom.repairdesk.feature.job.repository.JobRepository;
import com.acetelecom.repairdesk.feature.request.dto.RepairRequestSummary;
import com.acetelecom.repairdesk.feature.request.repository.RepairRequestRepository;
import com.acetelecom.repairdesk.feature.user.domain.Role;
import com.acetelecom.repairdesk.feature.user.domain.User;
import com.acetelecom.repairdesk.feature.user.dto.UserProfile;
import com.acetelecom.repairdesk.feature.user.repository.UserRepository;

@RestController
@RequestMapping("/customers")
@Secured({ "ROLE_STAFF", "ROLE_ADMIN" })
public class CustomerController {

    private final UserRepository userRepo;
    private final RepairRequestRepository requestRepo;
    private final JobRepository jobRepo;

    public CustomerController(UserRepository userRepo,
            RepairRequestRepository requestRepo,
            JobRepository jobRepo) {
        this.userRepo = userRepo;
        this.requestRepo = requestRepo;
        this.jobRepo = jobRepo;
    }

    /** Search customers (CUSTOMER role, non-deleted). */
    @GetMapping
    public List<UserProfile> search(@RequestParam(required = false) String q) {
        return userRepo.searchCustomers(Role.CUSTOMER, q).stream()
                .map(UserProfile::from)
                .toList();
    }

    /** Customer profile. */
    @GetMapping("/{id}")
    public UserProfile profile(@PathVariable UUID id) {
        User user = userRepo.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Customer not found"));
        if (user.getRole() != Role.CUSTOMER) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Customer not found");
        }
        return UserProfile.from(user);
    }

    /** Customer repair history: all requests and their jobs. */
    @GetMapping("/{id}/history")
    public CustomerHistory history(@PathVariable UUID id) {
        User user = userRepo.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Customer not found"));
        if (user.getRole() != Role.CUSTOMER) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Customer not found");
        }

        var requests = requestRepo.findByCustomerIdOrderByUpdatedAtDesc(id).stream()
                .map(RepairRequestSummary::from)
                .toList();
        var jobs = jobRepo.findByCustomerId(id).stream()
                .map(JobSummary::from)
                .toList();

        return new CustomerHistory(UserProfile.from(user), requests, jobs);
    }

    record CustomerHistory(UserProfile customer,
            List<RepairRequestSummary> requests,
            List<JobSummary> jobs) {
    }
}
