package com.acetelecom.repairdesk.feature.location.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acetelecom.repairdesk.feature.location.domain.PresenceState;
import com.acetelecom.repairdesk.feature.location.domain.TechnicianPresence;
import com.acetelecom.repairdesk.feature.location.dto.ReportLocationRequest;
import com.acetelecom.repairdesk.feature.location.repository.TechnicianPresenceRepository;
import com.acetelecom.repairdesk.security.AuthenticatedUser;

@Service
public class TechnicianPresenceService {

    private final TechnicianPresenceRepository repo;
    private final Clock clock;

    public TechnicianPresenceService(TechnicianPresenceRepository repo, Clock clock) {
        this.repo = repo;
        this.clock = clock;
    }

    @Transactional
    public void report(AuthenticatedUser technician, ReportLocationRequest body) {
        TechnicianPresence row = repo.findById(technician.id()).orElseGet(TechnicianPresence::new);
        row.setUserId(technician.id());
        row.setLat(body.latitude());
        row.setLng(body.longitude());
        row.setAccuracyM(body.accuracyM());
        row.setJobId(body.jobId());
        row.setState(body.state());
        row.setRecordedAt(clock.instant());
        repo.save(row);
    }

    /** From trip points. A newer stored position is kept. */
    @Transactional
    public void reportTravelling(UUID technicianId, UUID companyId, double latitude, double longitude,
            Double accuracyM, UUID jobId, Instant recordedAt) {
        TechnicianPresence row = repo.findById(technicianId).orElseGet(TechnicianPresence::new);
        if (row.getRecordedAt() != null && row.getRecordedAt().isAfter(recordedAt)) {
            return;
        }
        row.setUserId(technicianId);
        if (row.getCompanyId() == null) {
            row.setCompanyId(companyId);
        }
        row.setLat(latitude);
        row.setLng(longitude);
        row.setAccuracyM(accuracyM);
        row.setJobId(jobId);
        row.setState(PresenceState.TRAVELLING);
        row.setRecordedAt(recordedAt);
        repo.save(row);
    }
}
