package com.acetelecom.repairdesk.feature.trip.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acetelecom.repairdesk.common.error.ApiException;
import com.acetelecom.repairdesk.common.error.ErrorCode;
import com.acetelecom.repairdesk.feature.audit.service.AuditService;
import com.acetelecom.repairdesk.feature.company.CompanyContext;
import com.acetelecom.repairdesk.feature.job.domain.Job;
import com.acetelecom.repairdesk.feature.job.domain.JobStatus;
import com.acetelecom.repairdesk.feature.job.repository.JobRepository;
import com.acetelecom.repairdesk.feature.location.domain.LocationSettings;
import com.acetelecom.repairdesk.feature.location.domain.ServiceLocation;
import com.acetelecom.repairdesk.feature.location.domain.VehicleType;
import com.acetelecom.repairdesk.feature.location.repository.LocationSettingsRepository;
import com.acetelecom.repairdesk.feature.location.service.GeoMath;
import com.acetelecom.repairdesk.feature.location.service.MapsService;
import com.acetelecom.repairdesk.feature.location.service.MapsService.RouteEstimate;
import com.acetelecom.repairdesk.feature.location.service.TechnicianPresenceService;
import com.acetelecom.repairdesk.feature.settings.domain.BusinessSettings;
import com.acetelecom.repairdesk.feature.settings.repository.BusinessSettingsRepository;
import com.acetelecom.repairdesk.feature.trip.domain.JobNoTripReason;
import com.acetelecom.repairdesk.feature.trip.domain.JobTrip;
import com.acetelecom.repairdesk.feature.trip.domain.NoTripReason;
import com.acetelecom.repairdesk.feature.trip.domain.TripEndReason;
import com.acetelecom.repairdesk.feature.trip.domain.TripPoint;
import com.acetelecom.repairdesk.feature.trip.domain.TripStartKind;
import com.acetelecom.repairdesk.feature.trip.domain.TripStatus;
import com.acetelecom.repairdesk.feature.trip.dto.AdjustDistanceRequest;
import com.acetelecom.repairdesk.feature.trip.dto.EndTripRequest;
import com.acetelecom.repairdesk.feature.trip.dto.JobTripsResponse;
import com.acetelecom.repairdesk.feature.trip.dto.StartTripRequest;
import com.acetelecom.repairdesk.feature.trip.dto.TripPointsRequest;
import com.acetelecom.repairdesk.feature.trip.dto.TripPointsResponse;
import com.acetelecom.repairdesk.feature.trip.dto.TripResponse;
import com.acetelecom.repairdesk.feature.trip.repository.JobNoTripReasonRepository;
import com.acetelecom.repairdesk.feature.trip.repository.JobTripRepository;
import com.acetelecom.repairdesk.feature.trip.repository.TripPointRepository;
import com.acetelecom.repairdesk.feature.trip.service.TripDistanceCalculator.GapRouter;
import com.acetelecom.repairdesk.feature.trip.service.TripDistanceCalculator.Result;
import com.acetelecom.repairdesk.feature.user.domain.User;
import com.acetelecom.repairdesk.feature.user.repository.UserRepository;
import com.acetelecom.repairdesk.security.AuthenticatedUser;

/**
 * Job trips: start, point upload, end, the strict-mode check on job start, Staff
 * adjustments, and the scheduled timeout and point retention. Does not change a job.
 */
@Service
@Transactional
public class TripService {

    static final Duration LATE_POINTS_WINDOW = Duration.ofMinutes(30);
    static final Duration LATE_POINT_SLACK = Duration.ofMinutes(1);
    static final Duration TRIP_TIMEOUT = Duration.ofHours(6);
    static final Duration PREVIOUS_JOB_WINDOW = Duration.ofHours(24);
    static final String NO_TRIP_MESSAGE = "No trip recorded for this job";

    private static final String TRIP_NOT_FOUND = "Trip not found";
    private static final Set<JobStatus> TRIP_JOB_STATUSES = EnumSet.of(
            JobStatus.ASSIGNED, JobStatus.SCHEDULED, JobStatus.IN_PROGRESS, JobStatus.AWAITING_PARTS);
    private static final Set<TripEndReason> APP_END_REASONS = EnumSet.of(
            TripEndReason.ARRIVED, TripEndReason.GEOFENCE, TripEndReason.CANCELLED);
    private static final BigDecimal MAX_KM = new BigDecimal("1000");

    /** {@code created} is false when the same job already had a running trip. */
    public record StartResult(TripResponse trip, boolean created) {
    }

    private final JobTripRepository trips;
    private final TripPointRepository points;
    private final JobNoTripReasonRepository noTripReasons;
    private final JobRepository jobs;
    private final UserRepository users;
    private final LocationSettingsRepository locationRepo;
    private final BusinessSettingsRepository businessRepo;
    private final MapsService maps;
    private final TechnicianPresenceService presence;
    private final AuditService audit;
    private final TripViews views;
    private final Clock clock;

    public TripService(JobTripRepository trips, TripPointRepository points,
            JobNoTripReasonRepository noTripReasons, JobRepository jobs, UserRepository users,
            LocationSettingsRepository locationRepo, BusinessSettingsRepository businessRepo,
            MapsService maps, TechnicianPresenceService presence, AuditService audit,
            TripViews views, Clock clock) {
        this.trips = trips;
        this.points = points;
        this.noTripReasons = noTripReasons;
        this.jobs = jobs;
        this.users = users;
        this.locationRepo = locationRepo;
        this.businessRepo = businessRepo;
        this.maps = maps;
        this.presence = presence;
        this.audit = audit;
        this.views = views;
        this.clock = clock;
    }

    public StartResult start(UUID jobId, StartTripRequest body, AuthenticatedUser actor) {
        LocationSettings settings = location(null);
        if (!settings.isTravelEnabled()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Job trips are not turned on");
        }
        Job job = requireJob(jobId);
        if (job.getAssignedTechnician() == null || !job.getAssignedTechnician().getId().equals(actor.id())) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "This job is not assigned to you");
        }
        if (!TRIP_JOB_STATUSES.contains(job.getStatus())) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "A trip cannot start when the job is " + job.getStatus());
        }
        Double latitude = body == null ? null : body.latitude();
        Double longitude = body == null ? null : body.longitude();
        requirePair(latitude, longitude);

        Instant now = clock.instant();
        Optional<JobTrip> running = trips.findFirstByTechnicianIdAndStatusOrderByStartedAtDesc(
                actor.id(), TripStatus.ACTIVE);
        if (running.isPresent()) {
            JobTrip current = running.get();
            if (current.getJobId().equals(jobId)) {
                return new StartResult(views.toResponse(current, false), false);
            }
            finish(current, TripEndReason.REPLACED, null, now);
            // The one-active-trip index needs the old row ended before the new row is inserted.
            trips.saveAndFlush(current);
        }

        JobTrip trip = new JobTrip();
        trip.setJobId(jobId);
        trip.setTechnicianId(actor.id());
        trip.setStatus(TripStatus.ACTIVE);
        trip.setStartedAt(now);
        trip.setUpdatedAt(now);
        if (latitude != null) {
            trip.setStartLat(latitude);
            trip.setStartLng(longitude);
            trip.setStartAccuracyM(body.accuracyM());
            trip.setStartMocked(Boolean.TRUE.equals(body.mocked()));
        }
        applyStartKind(trip, job, settings, now);
        applyPlannedRoute(trip, job, users.findById(actor.id()).orElse(null));
        JobTrip saved = trips.saveAndFlush(trip);
        if (saved.hasStartFix()) {
            presence.reportTravelling(actor.id(), saved.getCompanyId(), saved.getStartLat(), saved.getStartLng(),
                    saved.getStartAccuracyM(), jobId, now);
        }
        return new StartResult(views.toResponse(saved, false), true);
    }

    public TripPointsResponse addPoints(UUID tripId, List<TripPointsRequest.Point> batch, AuthenticatedUser actor) {
        JobTrip trip = lockTrip(tripId);
        requireOwner(trip, actor);
        Instant now = clock.instant();
        boolean active = trip.getStatus() == TripStatus.ACTIVE;
        if (!active && (trip.getEndedAt() == null || now.isAfter(trip.getEndedAt().plus(LATE_POINTS_WINDOW)))) {
            return new TripPointsResponse(0, 0, trip.getTrackedDistanceM());
        }
        Instant latestAllowed = active ? null : trip.getEndedAt().plus(LATE_POINT_SLACK);
        List<TripPointsRequest.Point> input = batch == null ? List.of() : batch;

        Set<String> ids = new HashSet<>();
        for (TripPointsRequest.Point point : input) {
            ids.add(point.clientPointId());
        }
        Set<String> existing = ids.isEmpty()
                ? Set.of()
                : new HashSet<>(points.findExistingClientPointIds(trip.getId(), ids));
        Set<String> seen = new HashSet<>();
        int duplicates = 0;
        List<TripPoint> fresh = new ArrayList<>();
        for (TripPointsRequest.Point point : input) {
            if (existing.contains(point.clientPointId()) || !seen.add(point.clientPointId())) {
                duplicates++;
                continue;
            }
            if (latestAllowed != null && point.recordedAt().isAfter(latestAllowed)) {
                continue;
            }
            TripPoint row = new TripPoint();
            row.setCompanyId(trip.getCompanyId());
            row.setTripId(trip.getId());
            row.setClientPointId(point.clientPointId());
            row.setRecordedAt(point.recordedAt());
            row.setLat(point.latitude());
            row.setLng(point.longitude());
            row.setAccuracyM(point.accuracyM());
            row.setSpeedMps(point.speedMps());
            row.setMocked(Boolean.TRUE.equals(point.mocked()));
            row.setUpdatedAt(now);
            fresh.add(row);
        }
        if (fresh.isEmpty()) {
            return new TripPointsResponse(0, duplicates, trip.getTrackedDistanceM());
        }
        points.saveAllAndFlush(fresh);
        recalculate(trip, location(trip.getCompanyId()), trip.getStatus() == TripStatus.ENDED);
        trip.setUpdatedAt(now);
        trips.save(trip);

        if (active) {
            TripPoint newest = fresh.stream().max(Comparator.comparing(TripPoint::getRecordedAt)).orElseThrow();
            Instant at = newest.getRecordedAt().isAfter(now) ? now : newest.getRecordedAt();
            presence.reportTravelling(trip.getTechnicianId(), trip.getCompanyId(), newest.getLat(), newest.getLng(),
                    newest.getAccuracyM(), trip.getJobId(), at);
        }
        return new TripPointsResponse(fresh.size(), duplicates, trip.getTrackedDistanceM());
    }

    public TripResponse end(UUID tripId, EndTripRequest body, AuthenticatedUser actor) {
        TripEndReason reason = body == null ? null : body.reason();
        if (reason == null || !APP_END_REASONS.contains(reason)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Reason must be ARRIVED, GEOFENCE or CANCELLED");
        }
        JobTrip trip = lockTrip(tripId);
        requireOwner(trip, actor);
        if (trip.getStatus() != TripStatus.ACTIVE) {
            return views.toResponse(trip, false);
        }
        requirePair(body.latitude(), body.longitude());
        finish(trip, reason, body, clock.instant());
        trips.save(trip);
        return views.toResponse(trip, false);
    }

    @Transactional(readOnly = true)
    public Optional<TripResponse> active(AuthenticatedUser actor) {
        return trips.findFirstByTechnicianIdAndStatusOrderByStartedAtDesc(actor.id(), TripStatus.ACTIVE)
                .map(trip -> views.toResponse(trip, false));
    }

    /** Staff and Admin see every trip on the job. The assigned technician sees only their own. */
    @Transactional(readOnly = true)
    public JobTripsResponse jobTrips(UUID jobId, AuthenticatedUser actor) {
        Job job = requireJob(jobId);
        boolean staff = isStaff(actor);
        if (!staff) {
            boolean assigned = "TECHNICIAN".equals(actor.role())
                    && job.getAssignedTechnician() != null
                    && job.getAssignedTechnician().getId().equals(actor.id());
            if (!assigned) {
                throw new ApiException(ErrorCode.FORBIDDEN, "Access denied");
            }
        }
        List<JobTrip> list = trips.findByJobIdOrderByStartedAtAsc(jobId).stream()
                .filter(trip -> staff || trip.getTechnicianId().equals(actor.id()))
                .toList();
        JobNoTripReason latest = noTripReasons.findByJobIdOrderByCreatedAtDesc(jobId).stream()
                .filter(reason -> staff || reason.getTechnicianId().equals(actor.id()))
                .findFirst()
                .orElse(null);
        JobTripsResponse.NoTrip noTrip = null;
        if (latest != null) {
            noTrip = new JobTripsResponse.NoTrip(latest.getReasonCode(), latest.getReasonText(),
                    TripViews.displayName(users.findById(latest.getTechnicianId()).orElse(null)),
                    latest.getCreatedAt());
        }
        return new JobTripsResponse(views.toResponses(list, true), noTrip);
    }

    public TripResponse adjustDistance(UUID tripId, AdjustDistanceRequest body, AuthenticatedUser actor) {
        if (!isStaff(actor)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Staff or Admin role required");
        }
        BigDecimal km = body == null ? null : body.distanceKm();
        if (km == null || km.signum() < 0 || km.compareTo(MAX_KM) > 0) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Distance must be between 0 and 1000 km");
        }
        if (km.stripTrailingZeros().scale() > 1) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Distance can have one decimal place");
        }
        String reason = body.reason() == null ? "" : body.reason().trim();
        if (reason.length() < 3 || reason.length() > 500) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Reason must be 3 to 500 characters");
        }
        JobTrip trip = trips.findById(tripId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, TRIP_NOT_FOUND));
        CompanyContext.requireSame(trip.getCompanyId(), TRIP_NOT_FOUND);
        if (trip.getStatus() != TripStatus.ENDED) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Only ended trips can be adjusted");
        }
        int before = trip.payableDistanceM();
        int after = km.multiply(BigDecimal.valueOf(1000)).setScale(0, RoundingMode.HALF_UP).intValueExact();
        Instant now = clock.instant();
        trip.setAdjustedDistanceM(after);
        trip.setAdjustReason(reason);
        trip.setAdjustedBy(actor.id());
        trip.setAdjustedAt(now);
        trip.setUpdatedAt(now);
        trips.save(trip);
        audit.record(actor, "TRIP", trip.getId(), "DISTANCE_ADJUSTED",
                kilometres(before) + " → " + kilometres(after) + ": " + reason);
        return views.toResponse(trip, false);
    }

    /**
     * Runs inside job start, after the transition checks. Ends running trips for the
     * job, then applies strict mode to technicians.
     */
    public void beforeJobStart(Job job, AuthenticatedUser actor, NoTripReason reason, String note) {
        LocationSettings settings = location(job.getCompanyId());
        if (!settings.isTravelEnabled()) {
            return;
        }
        Instant now = clock.instant();
        boolean callerTripEnded = false;
        for (JobTrip running : trips.findByJobIdAndStatus(job.getId(), TripStatus.ACTIVE)) {
            finish(running, TripEndReason.JOB_STARTED, null, now);
            trips.save(running);
            if (running.getTechnicianId().equals(actor.id())) {
                callerTripEnded = true;
            }
        }
        if (!"TECHNICIAN".equals(actor.role()) || !settings.isStrictTripMode()) {
            return;
        }
        if (callerTripEnded
                || trips.existsByJobIdAndTechnicianIdAndStatus(job.getId(), actor.id(), TripStatus.ENDED)) {
            return;
        }
        if (reason == null) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, NO_TRIP_MESSAGE, Map.of("tripRequired", true));
        }
        String text = note == null || note.isBlank() ? null : note.trim();
        if (reason == NoTripReason.OTHER && text == null) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Enter a note for the reason Other");
        }
        if (text != null && text.length() > 500) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Note must be 500 characters or less");
        }
        JobNoTripReason row = new JobNoTripReason();
        row.setJobId(job.getId());
        row.setTechnicianId(actor.id());
        row.setReasonCode(reason);
        row.setReasonText(text);
        row.setUpdatedAt(now);
        noTripReasons.save(row);
    }

    /** Scheduled: running trips older than six hours end with TIMEOUT. */
    public int endTimedOutTrips() {
        Instant now = clock.instant();
        List<JobTrip> stale = trips.findByStatusAndStartedAtBefore(TripStatus.ACTIVE, now.minus(TRIP_TIMEOUT));
        for (JobTrip trip : stale) {
            finish(trip, TripEndReason.TIMEOUT, null, now);
            trips.save(trip);
        }
        return stale.size();
    }

    /** Scheduled: deletes points older than each company's retention. Trip totals stay. */
    public int deleteExpiredPoints() {
        Instant now = clock.instant();
        int deleted = 0;
        for (LocationSettings settings : locationRepo.findAll()) {
            Instant cutoff = now.minus(Duration.ofDays(settings.getRetentionDays()));
            deleted += points.deleteRecordedBefore(settings.getCompanyId(), cutoff);
        }
        return deleted;
    }

    private void finish(JobTrip trip, TripEndReason reason, EndTripRequest endFix, Instant now) {
        LocationSettings settings = location(trip.getCompanyId());
        trip.setStatus(reason == TripEndReason.CANCELLED ? TripStatus.CANCELLED : TripStatus.ENDED);
        trip.setEndedAt(now);
        trip.setEndReason(reason);
        if (endFix != null && endFix.latitude() != null && endFix.longitude() != null) {
            trip.setEndLat(endFix.latitude());
            trip.setEndLng(endFix.longitude());
            trip.setEndAccuracyM(endFix.accuracyM());
        }
        VehicleType vehicle = vehicleOf(users.findById(trip.getTechnicianId()).orElse(null));
        trip.setVehicleType(vehicle);
        trip.setRatePaise(vehicle == VehicleType.FOUR_WHEELER
                ? settings.getRateFourWheelerPaise()
                : settings.getRateTwoWheelerPaise());
        recalculate(trip, settings, trip.getStatus() == TripStatus.ENDED);
        trip.setUpdatedAt(now);
    }

    /** {@code routeGaps} asks Google for gap distances; otherwise gaps use the straight-line estimate. */
    private void recalculate(JobTrip trip, LocationSettings settings, boolean routeGaps) {
        List<TripPoint> stored = points.findByTripIdOrderByRecordedAtAsc(trip.getId());
        GapRouter router = routeGaps ? routedGaps(trip.getVehicleType()) : TripDistanceCalculator::straightLineGap;
        Result result = TripDistanceCalculator.calculate(
                TripViews.startFix(trip), TripViews.fixes(stored), TripViews.endFix(trip), router);
        trip.setTrackedDistanceM(result.trackedDistanceM());
        trip.setEstimatedGapM(result.estimatedGapM());
        trip.setGapCount(result.gapCount());
        trip.setMockedPoints(result.mockedPoints());
        trip.setPointCount(stored.size());
        if (trip.getStatus() == TripStatus.ACTIVE) {
            trip.replaceFlags(null);
            return;
        }
        ServiceLocation pin = jobs.findById(trip.getJobId()).map(Job::getServiceLocation).orElse(null);
        trip.replaceFlags(TripDistanceCalculator.flags(result, trip.getPlannedDistanceM(), trip.getEndReason(),
                pin == null ? null : pin.getLatitude(), pin == null ? null : pin.getLongitude(),
                settings.getFarFromAddressM()));
    }

    private GapRouter routedGaps(VehicleType vehicle) {
        String mode = travelMode(vehicle);
        return (fromLat, fromLng, toLat, toLng) -> maps.route(fromLat, fromLng, toLat, toLng, mode).distanceMetres();
    }

    private void applyStartKind(JobTrip trip, Job job, LocationSettings settings, Instant now) {
        if (!trip.hasStartFix()) {
            trip.setStartKind(TripStartKind.UNKNOWN);
            return;
        }
        double lat = trip.getStartLat();
        double lng = trip.getStartLng();
        BusinessSettings business = business(job.getCompanyId());
        if (business.getShopLat() != null && business.getShopLng() != null
                && GeoMath.withinMetres(lat, lng, business.getShopLat(), business.getShopLng(),
                        business.getGeofenceRadiusM())) {
            trip.setStartKind(TripStartKind.SHOP);
            return;
        }
        Job nearest = null;
        double nearestMetres = Double.MAX_VALUE;
        for (Job other : jobs.findPinnedForTechnicianSince(trip.getTechnicianId(), job.getId(),
                now.minus(PREVIOUS_JOB_WINDOW))) {
            ServiceLocation pin = other.getServiceLocation();
            if (pin == null || pin.getLatitude() == null || pin.getLongitude() == null) {
                continue;
            }
            double metres = GeoMath.metresBetween(lat, lng, pin.getLatitude(), pin.getLongitude());
            if (metres <= settings.getFarFromAddressM() && metres < nearestMetres) {
                nearest = other;
                nearestMetres = metres;
            }
        }
        if (nearest != null) {
            trip.setStartKind(TripStartKind.PREVIOUS_JOB);
            trip.setStartJobId(nearest.getId());
            return;
        }
        trip.setStartKind(TripStartKind.OTHER);
    }

    private void applyPlannedRoute(JobTrip trip, Job job, User technician) {
        ServiceLocation pin = job.getServiceLocation();
        if (!trip.hasStartFix() || pin == null || pin.getLatitude() == null || pin.getLongitude() == null) {
            return;
        }
        RouteEstimate route = maps.route(trip.getStartLat(), trip.getStartLng(),
                pin.getLatitude(), pin.getLongitude(), travelMode(vehicleOf(technician)));
        trip.setPlannedDistanceM(route.distanceMetres());
        trip.setPlannedDurationS(route.durationSeconds());
        trip.setPlannedEstimated(route.estimated());
    }

    private JobTrip lockTrip(UUID tripId) {
        JobTrip trip = trips.findForUpdate(tripId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, TRIP_NOT_FOUND));
        CompanyContext.requireSame(trip.getCompanyId(), TRIP_NOT_FOUND);
        return trip;
    }

    private Job requireJob(UUID jobId) {
        Job job = jobs.findById(jobId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Job not found"));
        CompanyContext.requireSame(job.getCompanyId(), "Job not found");
        return job;
    }

    private LocationSettings location(UUID companyId) {
        UUID company = companyId != null ? companyId : CompanyContext.get();
        if (company != null) {
            return locationRepo.findByCompanyId(company)
                    .orElseThrow(() -> new ApiException(ErrorCode.INTERNAL_ERROR, "Location settings not initialised"));
        }
        return locationRepo.findById((short) 1)
                .orElseThrow(() -> new ApiException(ErrorCode.INTERNAL_ERROR, "Location settings not initialised"));
    }

    private BusinessSettings business(UUID companyId) {
        UUID company = companyId != null ? companyId : CompanyContext.get();
        if (company != null) {
            return businessRepo.findByCompanyId(company)
                    .orElseThrow(() -> new ApiException(ErrorCode.INTERNAL_ERROR, "Business settings not initialised"));
        }
        return businessRepo.findById((short) 1)
                .orElseThrow(() -> new ApiException(ErrorCode.INTERNAL_ERROR, "Business settings not initialised"));
    }

    private static void requireOwner(JobTrip trip, AuthenticatedUser actor) {
        if (!trip.getTechnicianId().equals(actor.id())) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Access denied");
        }
    }

    private static void requirePair(Double latitude, Double longitude) {
        if ((latitude == null) != (longitude == null)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Latitude and longitude must be sent together");
        }
    }

    private static boolean isStaff(AuthenticatedUser actor) {
        return "STAFF".equals(actor.role()) || "ADMIN".equals(actor.role());
    }

    static VehicleType vehicleOf(User technician) {
        if (technician == null || technician.getVehicleType() == null) {
            return VehicleType.TWO_WHEELER;
        }
        return technician.getVehicleType();
    }

    private static String travelMode(VehicleType vehicle) {
        return vehicle == VehicleType.FOUR_WHEELER ? "DRIVE" : "TWO_WHEELER";
    }

    private static String kilometres(int metres) {
        return String.format(Locale.ROOT, "%.1f km", metres / 1000d);
    }
}
