package com.acetelecom.repairdesk.feature.location.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acetelecom.repairdesk.feature.company.CompanyContext;
import com.acetelecom.repairdesk.feature.location.domain.PresenceState;
import com.acetelecom.repairdesk.feature.location.domain.TechnicianLocationLog;
import com.acetelecom.repairdesk.feature.location.domain.TechnicianPresence;
import com.acetelecom.repairdesk.feature.location.dto.ReportLocationRequest;
import com.acetelecom.repairdesk.feature.location.repository.TechnicianLocationLogRepository;
import com.acetelecom.repairdesk.feature.location.repository.TechnicianPresenceRepository;
import com.acetelecom.repairdesk.security.AuthenticatedUser;

@Service
public class TechnicianPresenceService {

    private final TechnicianPresenceRepository repo;
    private final TechnicianLocationLogRepository logs;
    private final Clock clock;

    public TechnicianPresenceService(TechnicianPresenceRepository repo, TechnicianLocationLogRepository logs,
            Clock clock) {
        this.repo = repo;
        this.logs = logs;
        this.clock = clock;
    }

    @Transactional
    public void report(AuthenticatedUser technician, ReportLocationRequest body) {
        TechnicianPresence row = repo.findById(technician.id()).orElseGet(TechnicianPresence::new);
        Instant now = clock.instant();
        row.setUserId(technician.id());
        row.setLat(body.latitude());
        row.setLng(body.longitude());
        row.setAccuracyM(body.accuracyM());
        row.setJobId(body.jobId());
        row.setState(body.state());
        row.setRecordedAt(now);
        repo.save(row);
        remember(technician.id(), companyOf(row), body.latitude(), body.longitude(), body.accuracyM(), body.jobId(),
                body.state(), now);
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
        remember(technicianId, companyOf(row), latitude, longitude, accuracyM, jobId, PresenceState.TRAVELLING,
                recordedAt);
    }

    /** The Locations tab keeps this share for two days. */
    private void remember(UUID userId, UUID companyId, double latitude, double longitude, Double accuracyM,
            UUID jobId, PresenceState state, Instant recordedAt) {
        if (companyId == null || recordedAt == null) {
            return;
        }
        TechnicianLocationLog entry = new TechnicianLocationLog();
        entry.setCompanyId(companyId);
        entry.setUserId(userId);
        entry.setLat(latitude);
        entry.setLng(longitude);
        entry.setAccuracyM(accuracyM);
        entry.setJobId(jobId);
        entry.setState(state);
        entry.setRecordedAt(recordedAt);
        entry.setUpdatedAt(recordedAt);
        logs.save(entry);
    }

    private static UUID companyOf(TechnicianPresence row) {
        return row.getCompanyId() != null ? row.getCompanyId() : CompanyContext.get();
    }
}
