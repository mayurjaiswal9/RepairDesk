package com.acetelecom.repairdesk.feature.location.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acetelecom.repairdesk.feature.job.domain.Job;
import com.acetelecom.repairdesk.feature.job.repository.JobRepository;
import com.acetelecom.repairdesk.feature.location.domain.TechnicianLocationLog;
import com.acetelecom.repairdesk.feature.location.dto.LocationHistoryResponse;
import com.acetelecom.repairdesk.feature.location.dto.LocationHistoryResponse.Entry;
import com.acetelecom.repairdesk.feature.location.repository.TechnicianLocationLogRepository;
import com.acetelecom.repairdesk.feature.user.domain.User;
import com.acetelecom.repairdesk.feature.user.repository.UserRepository;

@Service
public class LocationHistoryService {

    private final TechnicianLocationLogRepository repo;
    private final UserRepository users;
    private final JobRepository jobs;
    private final Clock clock;

    public LocationHistoryService(TechnicianLocationLogRepository repo, UserRepository users, JobRepository jobs,
            Clock clock) {
        this.repo = repo;
        this.users = users;
        this.jobs = jobs;
        this.clock = clock;
    }

    /** {@code requested} outside the two-day window falls back to today in India. */
    @Transactional(readOnly = true)
    public LocationHistoryResponse history(LocalDate requested) {
        Instant now = clock.instant();
        List<LocalDate> days = LocationHistoryWindow.days(now);
        LocalDate date = requested != null && days.contains(requested) ? requested : days.get(0);
        Instant from = LocationHistoryWindow.startOfDay(date);
        Instant to = LocationHistoryWindow.startOfDay(date.plusDays(1));
        Instant cutoff = LocationHistoryWindow.cutoff(now);
        if (from.isBefore(cutoff)) {
            from = cutoff;
        }
        List<TechnicianLocationLog> rows = from.isBefore(to)
                ? repo.findByRecordedAtGreaterThanEqualAndRecordedAtLessThanOrderByRecordedAtDesc(from, to)
                : List.of();
        Map<UUID, User> byUser = new HashMap<>();
        for (User user : users.findAllById(rows.stream().map(TechnicianLocationLog::getUserId).distinct().toList())) {
            byUser.put(user.getId(), user);
        }
        Map<UUID, String> referenceByJob = new HashMap<>();
        for (Job job : jobs.findAllById(rows.stream().map(TechnicianLocationLog::getJobId).filter(id -> id != null).distinct().toList())) {
            referenceByJob.put(job.getId(), job.getReferenceNumber());
        }
        List<Entry> locations = rows.stream().map(row -> new Entry(
                row.getId(),
                row.getUserId(),
                nameOf(byUser.get(row.getUserId())),
                row.getLat(),
                row.getLng(),
                row.getRecordedAt(),
                row.getJobId(),
                row.getJobId() == null ? null : referenceByJob.get(row.getJobId()),
                row.getState() == null ? null : row.getState().name())).toList();
        return new LocationHistoryResponse(date, days, locations);
    }

    @Transactional
    public int deleteExpired(Instant now) {
        return repo.deleteRecordedBefore(LocationHistoryWindow.cutoff(now));
    }

    private static String nameOf(User user) {
        if (user == null) {
            return "Technician";
        }
        if (user.getName() != null && !user.getName().isBlank()) {
            return user.getName().trim();
        }
        return user.getUsername();
    }
}
