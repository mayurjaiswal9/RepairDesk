package com.acetelecom.repairdesk.feature.trip.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.acetelecom.repairdesk.feature.job.domain.Job;
import com.acetelecom.repairdesk.feature.job.repository.JobRepository;
import com.acetelecom.repairdesk.feature.location.domain.ServiceLocation;
import com.acetelecom.repairdesk.feature.trip.domain.JobTrip;
import com.acetelecom.repairdesk.feature.trip.domain.TripPoint;
import com.acetelecom.repairdesk.feature.trip.domain.TripStartKind;
import com.acetelecom.repairdesk.feature.trip.dto.GeoPoint;
import com.acetelecom.repairdesk.feature.trip.dto.TripResponse;
import com.acetelecom.repairdesk.feature.trip.repository.TripPointRepository;
import com.acetelecom.repairdesk.feature.trip.service.TripDistanceCalculator.Fix;
import com.acetelecom.repairdesk.feature.user.domain.User;
import com.acetelecom.repairdesk.feature.user.repository.UserRepository;

/** Builds {@link TripResponse} rows with job references, names and, when asked, the route line. */
@Component
public class TripViews {

    private final JobRepository jobs;
    private final UserRepository users;
    private final TripPointRepository points;
    private final Clock clock;

    public TripViews(JobRepository jobs, UserRepository users, TripPointRepository points, Clock clock) {
        this.jobs = jobs;
        this.users = users;
        this.points = points;
        this.clock = clock;
    }

    public TripResponse toResponse(JobTrip trip, boolean withRoute) {
        return toResponses(List.of(trip), withRoute).get(0);
    }

    public List<TripResponse> toResponses(List<JobTrip> trips, boolean withRoute) {
        if (trips.isEmpty()) {
            return List.of();
        }
        Set<UUID> jobIds = new HashSet<>();
        Set<UUID> userIds = new HashSet<>();
        for (JobTrip trip : trips) {
            jobIds.add(trip.getJobId());
            if (trip.getStartJobId() != null) {
                jobIds.add(trip.getStartJobId());
            }
            userIds.add(trip.getTechnicianId());
            if (trip.getAdjustedBy() != null) {
                userIds.add(trip.getAdjustedBy());
            }
        }
        Map<UUID, Job> jobsById = new HashMap<>();
        for (Job job : jobs.findAllById(jobIds)) {
            jobsById.put(job.getId(), job);
        }
        Map<UUID, User> usersById = new HashMap<>();
        for (User user : users.findAllById(userIds)) {
            usersById.put(user.getId(), user);
        }
        Instant now = clock.instant();
        List<TripResponse> responses = new ArrayList<>();
        for (JobTrip trip : trips) {
            responses.add(toResponse(trip, jobsById, usersById, now, withRoute ? route(trip) : List.of()));
        }
        return responses;
    }

    /** Cleaned points, simplified for drawing. */
    List<double[]> route(JobTrip trip) {
        List<TripPoint> stored = points.findByTripIdOrderByRecordedAtAsc(trip.getId());
        TripDistanceCalculator.Result result = TripDistanceCalculator.calculate(
                startFix(trip), fixes(stored), endFix(trip), TripDistanceCalculator::straightLineGap);
        List<double[]> line = new ArrayList<>();
        for (Fix fix : result.accepted()) {
            line.add(new double[] { round6(fix.latitude()), round6(fix.longitude()) });
        }
        return RouteSimplifier.simplify(line);
    }

    static Fix startFix(JobTrip trip) {
        if (!trip.hasStartFix()) {
            return null;
        }
        return new Fix(trip.getStartedAt(), trip.getStartLat(), trip.getStartLng(),
                trip.getStartAccuracyM(), trip.isStartMocked());
    }

    static Fix endFix(JobTrip trip) {
        if (!trip.hasEndFix() || trip.getEndedAt() == null) {
            return null;
        }
        return new Fix(trip.getEndedAt(), trip.getEndLat(), trip.getEndLng(), trip.getEndAccuracyM(), false);
    }

    static List<Fix> fixes(List<TripPoint> stored) {
        List<Fix> fixes = new ArrayList<>(stored.size());
        for (TripPoint point : stored) {
            fixes.add(new Fix(point.getRecordedAt(), point.getLat(), point.getLng(),
                    point.getAccuracyM(), point.isMocked()));
        }
        return fixes;
    }

    public static String displayName(User user) {
        if (user == null) {
            return null;
        }
        if (user.getName() != null && !user.getName().isBlank()) {
            return user.getName();
        }
        return user.getUsername();
    }

    private static TripResponse toResponse(JobTrip trip, Map<UUID, Job> jobsById, Map<UUID, User> usersById,
            Instant now, List<double[]> route) {
        Job job = jobsById.get(trip.getJobId());
        Job startJob = trip.getStartJobId() == null ? null : jobsById.get(trip.getStartJobId());
        ServiceLocation pin = job == null ? null : job.getServiceLocation();
        Instant until = trip.getEndedAt() == null ? now : trip.getEndedAt();
        long duration = Math.max(0L, Duration.between(trip.getStartedAt(), until).getSeconds());
        return new TripResponse(
                trip.getId(),
                trip.getJobId(),
                job == null ? null : job.getReferenceNumber(),
                trip.getTechnicianId(),
                displayName(usersById.get(trip.getTechnicianId())),
                trip.getStatus(),
                trip.getStartedAt(),
                trip.getEndedAt(),
                duration,
                trip.getStartKind(),
                trip.getStartKind() == TripStartKind.PREVIOUS_JOB && startJob != null
                        ? startJob.getReferenceNumber()
                        : null,
                GeoPoint.of(trip.getStartLat(), trip.getStartLng()),
                GeoPoint.of(trip.getEndLat(), trip.getEndLng()),
                pin == null ? null : GeoPoint.of(pin.getLatitude(), pin.getLongitude()),
                trip.getTrackedDistanceM(),
                trip.getEstimatedGapM(),
                trip.getPlannedDistanceM(),
                trip.getPlannedDurationS(),
                trip.isPlannedEstimated(),
                trip.getAdjustedDistanceM(),
                trip.getAdjustReason(),
                trip.getAdjustedBy() == null ? null : displayName(usersById.get(trip.getAdjustedBy())),
                trip.getAdjustedAt(),
                trip.payableDistanceM(),
                trip.getVehicleType(),
                trip.getRatePaise(),
                trip.amountPaise(),
                trip.flagNames(),
                trip.getPointCount(),
                trip.getEndReason(),
                route);
    }

    private static double round6(double value) {
        return Math.round(value * 1_000_000d) / 1_000_000d;
    }
}
