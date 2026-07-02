package com.acetelecom.repairdesk.feature.dashboard.controller;

import java.util.Map;

import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.acetelecom.repairdesk.feature.job.domain.JobStatus;
import com.acetelecom.repairdesk.feature.job.repository.JobRepository;
import com.acetelecom.repairdesk.feature.request.domain.RequestStatus;
import com.acetelecom.repairdesk.feature.request.repository.RepairRequestRepository;

@RestController
@RequestMapping("/dashboard")
@Secured({ "ROLE_STAFF", "ROLE_ADMIN" })
public class DashboardController {

    private final RepairRequestRepository requestRepo;
    private final JobRepository jobRepo;

    public DashboardController(RepairRequestRepository requestRepo, JobRepository jobRepo) {
        this.requestRepo = requestRepo;
        this.jobRepo = jobRepo;
    }

    @GetMapping("/counters")
    public Map<String, Long> counters() {
        return Map.of(
                "pendingReview", requestRepo.countByStatus(RequestStatus.PENDING_REVIEW.name()),
                "infoRequired", requestRepo.countByStatus(RequestStatus.INFORMATION_REQUIRED.name()),
                "activeJobs", jobRepo.countByStatus(JobStatus.IN_PROGRESS)
                        + jobRepo.countByStatus(JobStatus.AWAITING_PARTS)
                        + jobRepo.countByStatus(JobStatus.ASSIGNED)
                        + jobRepo.countByStatus(JobStatus.SCHEDULED));
    }
}
