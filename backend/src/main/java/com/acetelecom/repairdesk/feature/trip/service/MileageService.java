package com.acetelecom.repairdesk.feature.trip.service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
import com.acetelecom.repairdesk.feature.company.CompanyContext;
import com.acetelecom.repairdesk.feature.job.domain.Job;
import com.acetelecom.repairdesk.feature.job.repository.JobRepository;
import com.acetelecom.repairdesk.feature.location.domain.VehicleType;
import com.acetelecom.repairdesk.feature.settings.domain.BusinessSettings;
import com.acetelecom.repairdesk.feature.settings.repository.BusinessSettingsRepository;
import com.acetelecom.repairdesk.feature.trip.domain.JobNoTripReason;
import com.acetelecom.repairdesk.feature.trip.domain.JobTrip;
import com.acetelecom.repairdesk.feature.trip.domain.TripFlag;
import com.acetelecom.repairdesk.feature.trip.domain.TripStatus;
import com.acetelecom.repairdesk.feature.trip.dto.GeoPoint;
import com.acetelecom.repairdesk.feature.trip.dto.MileageDayResponse;
import com.acetelecom.repairdesk.feature.trip.dto.MileageDaysResponse;
import com.acetelecom.repairdesk.feature.trip.dto.MileageRow;
import com.acetelecom.repairdesk.feature.trip.dto.MileageSummary;
import com.acetelecom.repairdesk.feature.trip.repository.JobNoTripReasonRepository;
import com.acetelecom.repairdesk.feature.trip.repository.JobTripRepository;
import com.acetelecom.repairdesk.feature.user.domain.User;
import com.acetelecom.repairdesk.feature.user.repository.UserRepository;
import com.acetelecom.repairdesk.security.AuthenticatedUser;

/**
 * Mileage per technician per India date or month, worked out from trips when asked.
 * Cancelled trips are left out of every total.
 */
@Service
@Transactional(readOnly = true)
public class MileageService {

    static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    static final int MAX_RANGE_DAYS = 93;

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm").withZone(IST);

    enum View { DAY, MONTH }

    enum Show { ALL, FLAGGED, NO_TRIP }

    enum Level { DAY, TRIP }

    private final JobTripRepository trips;
    private final JobNoTripReasonRepository noTripReasons;
    private final JobRepository jobs;
    private final UserRepository users;
    private final BusinessSettingsRepository businessRepo;
    private final TripViews views;
    private final Clock clock;

    public MileageService(JobTripRepository trips, JobNoTripReasonRepository noTripReasons, JobRepository jobs,
            UserRepository users, BusinessSettingsRepository businessRepo, TripViews views, Clock clock) {
        this.trips = trips;
        this.noTripReasons = noTripReasons;
        this.jobs = jobs;
        this.users = users;
        this.businessRepo = businessRepo;
        this.views = views;
        this.clock = clock;
    }

    /** Technicians only ever see their own rows. */
    public MileageDaysResponse days(LocalDate from, LocalDate to, UUID technicianId, String vehicle, String show,
            String view, AuthenticatedUser actor) {
        Range range = range(from, to);
        View period = parse(View.class, view, View.DAY, "view");
        UUID technician = "TECHNICIAN".equals(actor.role()) ? actor.id() : technicianId;
        List<Bucket> buckets = buckets(range, technician, parse(VehicleType.class, vehicle, null, "vehicle"),
                parse(Show.class, show, Show.ALL, "show"), period);
        List<MileageRow> rows = buckets.stream().map(Bucket::row).toList();
        return new MileageDaysResponse(range.from(), range.to(), period.name(), summary(rows), rows);
    }

    public MileageDayResponse day(UUID technicianId, LocalDate date, AuthenticatedUser actor) {
        return day(technicianId, date, date, actor);
    }

    public MileageDayResponse day(UUID technicianId, LocalDate from, LocalDate to, AuthenticatedUser actor) {
        if ("TECHNICIAN".equals(actor.role()) && !actor.id().equals(technicianId)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Access denied");
        }
        User technician = users.findById(technicianId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Technician not found"));
        CompanyContext.requireSame(technician.getCompanyId(), "Technician not found");

        Range span = range(from, to == null ? from : to);
        Instant start = span.from().atStartOfDay(IST).toInstant();
        Instant end = span.to().plusDays(1).atStartOfDay(IST).toInstant();
        List<JobTrip> dayTrips = trips
                .findByTechnicianIdAndStartedAtGreaterThanEqualAndStartedAtLessThanOrderByStartedAtAsc(
                        technicianId, start, end);
        List<JobNoTripReason> reasons = noTripReasons
                .findByTechnicianIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(
                        technicianId, start, end);

        Bucket bucket = new Bucket(technicianId, technician, span.from(), null);
        for (JobTrip trip : dayTrips) {
            if (trip.getStatus() != TripStatus.CANCELLED) {
                bucket.trips.add(trip);
            }
        }
        Set<UUID> reasonJobIds = new HashSet<>();
        for (JobNoTripReason reason : reasons) {
            bucket.noTripJobIds.add(reason.getJobId());
            reasonJobIds.add(reason.getJobId());
        }
        MileageRow row = bucket.row();

        Map<UUID, Job> jobsById = new HashMap<>();
        for (Job job : jobs.findAllById(reasonJobIds)) {
            jobsById.put(job.getId(), job);
        }
        List<MileageDayResponse.NoTripJob> noTrip = new ArrayList<>();
        for (JobNoTripReason reason : reasons) {
            Job job = jobsById.get(reason.getJobId());
            noTrip.add(new MileageDayResponse.NoTripJob(reason.getJobId(),
                    job == null ? null : job.getReferenceNumber(), reason.getReasonCode(),
                    reason.getReasonText(), reason.getCreatedAt()));
        }
        GeoPoint shop = business().map(b -> GeoPoint.of(b.getShopLat(), b.getShopLng())).orElse(null);
        return new MileageDayResponse(span.from(), technicianId, TripViews.displayName(technician), row.vehicleType(),
                shop, summary(List.of(row)), views.toResponses(dayTrips, true), noTrip);
    }

    /** UTF-8 with a BOM so Excel opens it correctly. {@code level=TRIP} lists ENDED and ACTIVE trips. */
    public byte[] csv(LocalDate from, LocalDate to, UUID technicianId, String vehicle, String show, String view,
            String level) {
        Range range = range(from, to);
        View period = parse(View.class, view, View.DAY, "view");
        Show filter = parse(Show.class, show, Show.ALL, "show");
        Level detail = parse(Level.class, level, Level.DAY, "level");
        List<Bucket> buckets = buckets(range, technicianId, parse(VehicleType.class, vehicle, null, "vehicle"),
                filter, period);
        StringBuilder csv = new StringBuilder();
        if (detail == Level.DAY) {
            dayCsv(csv, buckets, period);
        } else {
            tripCsv(csv, buckets, filter);
        }
        byte[] text = csv.toString().getBytes(StandardCharsets.UTF_8);
        byte[] body = new byte[text.length + 3];
        body[0] = (byte) 0xEF;
        body[1] = (byte) 0xBB;
        body[2] = (byte) 0xBF;
        System.arraycopy(text, 0, body, 3, text.length);
        return body;
    }

    private List<Bucket> buckets(Range range, UUID technicianId, VehicleType vehicle, Show show, View view) {
        Instant start = range.from().atStartOfDay(IST).toInstant();
        Instant end = range.to().plusDays(1).atStartOfDay(IST).toInstant();
        List<JobTrip> tripList = technicianId == null
                ? trips.findByStartedAtGreaterThanEqualAndStartedAtLessThanOrderByStartedAtAsc(start, end)
                : trips.findByTechnicianIdAndStartedAtGreaterThanEqualAndStartedAtLessThanOrderByStartedAtAsc(
                        technicianId, start, end);
        List<JobNoTripReason> reasons = technicianId == null
                ? noTripReasons.findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(start, end)
                : noTripReasons
                        .findByTechnicianIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(
                                technicianId, start, end);

        Set<UUID> technicianIds = new HashSet<>();
        tripList.forEach(trip -> technicianIds.add(trip.getTechnicianId()));
        reasons.forEach(reason -> technicianIds.add(reason.getTechnicianId()));
        Map<UUID, User> people = new HashMap<>();
        for (User user : users.findAllById(technicianIds)) {
            people.put(user.getId(), user);
        }

        Map<String, Bucket> byKey = new LinkedHashMap<>();
        for (JobTrip trip : tripList) {
            if (trip.getStatus() == TripStatus.CANCELLED) {
                continue;
            }
            User technician = people.get(trip.getTechnicianId());
            if (vehicle != null && effectiveVehicle(trip, technician) != vehicle) {
                continue;
            }
            bucket(byKey, trip.getTechnicianId(), technician, indiaDate(trip.getStartedAt()), view).trips.add(trip);
        }
        for (JobNoTripReason reason : reasons) {
            User technician = people.get(reason.getTechnicianId());
            if (vehicle != null && TripService.vehicleOf(technician) != vehicle) {
                continue;
            }
            bucket(byKey, reason.getTechnicianId(), technician, indiaDate(reason.getCreatedAt()), view)
                    .noTripJobIds.add(reason.getJobId());
        }

        List<Bucket> kept = new ArrayList<>();
        for (Bucket bucket : byKey.values()) {
            MileageRow row = bucket.row();
            if (show == Show.FLAGGED && row.flaggedTrips() == 0) {
                continue;
            }
            if (show == Show.NO_TRIP && row.noTripJobs() == 0) {
                continue;
            }
            kept.add(bucket);
        }
        kept.sort(Comparator.comparing(Bucket::periodStart).reversed()
                .thenComparing(bucket -> sortName(bucket.technician))
                .thenComparing(bucket -> bucket.technicianId));
        return kept;
    }

    private static Bucket bucket(Map<String, Bucket> byKey, UUID technicianId, User technician, LocalDate date,
            View view) {
        YearMonth month = view == View.MONTH ? YearMonth.from(date) : null;
        String key = technicianId + "|" + (month == null ? date.toString() : month.toString());
        return byKey.computeIfAbsent(key,
                ignored -> new Bucket(technicianId, technician, month == null ? date : null, month));
    }

    private static MileageSummary summary(List<MileageRow> rows) {
        long payable = 0;
        int tripCount = 0;
        long amount = 0;
        int flagged = 0;
        int noTrip = 0;
        int active = 0;
        for (MileageRow row : rows) {
            payable += row.payableDistanceM();
            tripCount += row.trips();
            amount += row.amountPaise();
            flagged += row.flaggedTrips();
            noTrip += row.noTripJobs();
            active += row.activeTrips();
        }
        return new MileageSummary(payable, tripCount, amount, flagged, noTrip, active);
    }

    private void dayCsv(StringBuilder csv, List<Bucket> buckets, View view) {
        csv.append(view == View.MONTH ? "Month" : "Date")
                .append(",Technician,Vehicle,Trips,Tracked km,Adjustment km,Payable km,Rate per km,Amount,"
                        + "Flagged trips,No-trip jobs,Active trips\r\n");
        for (Bucket bucket : buckets) {
            MileageRow row = bucket.row();
            csv.append(view == View.MONTH ? bucket.month.format(MONTH) : bucket.date.format(DATE)).append(',')
                    .append(cell(row.technicianName())).append(',')
                    .append(vehicleText(row.vehicleType())).append(',')
                    .append(row.trips()).append(',')
                    .append(km(row.trackedDistanceM())).append(',')
                    .append(km(row.adjustmentM())).append(',')
                    .append(km(row.payableDistanceM())).append(',')
                    .append(row.ratePaise() == null ? "" : rupees(row.ratePaise())).append(',')
                    .append(rupees(row.amountPaise())).append(',')
                    .append(row.flaggedTrips()).append(',')
                    .append(row.noTripJobs()).append(',')
                    .append(row.activeTrips())
                    .append("\r\n");
        }
    }

    private void tripCsv(StringBuilder csv, List<Bucket> buckets, Show show) {
        csv.append("Date,Technician,Vehicle,Job,From,To,Start time,End time,Minutes,Status,End reason,"
                + "Tracked km,Route km,Adjusted km,Payable km,Rate per km,Amount,Flags,Adjusted by,Adjust reason\r\n");
        Set<UUID> jobIds = new HashSet<>();
        Set<UUID> adjusterIds = new HashSet<>();
        for (Bucket bucket : buckets) {
            for (JobTrip trip : bucket.trips) {
                jobIds.add(trip.getJobId());
                if (trip.getStartJobId() != null) {
                    jobIds.add(trip.getStartJobId());
                }
                if (trip.getAdjustedBy() != null) {
                    adjusterIds.add(trip.getAdjustedBy());
                }
            }
        }
        Map<UUID, Job> jobsById = new HashMap<>();
        for (Job job : jobs.findAllById(jobIds)) {
            jobsById.put(job.getId(), job);
        }
        Map<UUID, User> adjusters = new HashMap<>();
        for (User user : users.findAllById(adjusterIds)) {
            adjusters.put(user.getId(), user);
        }
        Instant now = clock.instant();
        for (Bucket bucket : buckets) {
            for (JobTrip trip : bucket.trips) {
                if (show == Show.FLAGGED && trip.flagNames().isEmpty()) {
                    continue;
                }
                Job job = jobsById.get(trip.getJobId());
                Instant until = trip.getEndedAt() == null ? now : trip.getEndedAt();
                Integer amount = trip.amountPaise();
                csv.append(indiaDate(trip.getStartedAt()).format(DATE)).append(',')
                        .append(cell(TripViews.displayName(bucket.technician))).append(',')
                        .append(vehicleText(effectiveVehicle(trip, bucket.technician))).append(',')
                        .append(cell(job == null ? null : job.getReferenceNumber())).append(',')
                        .append(cell(fromText(trip, jobsById))).append(',')
                        .append(cell(job == null ? null : job.getReferenceNumber())).append(',')
                        .append(CLOCK.format(trip.getStartedAt())).append(',')
                        .append(trip.getEndedAt() == null ? "" : CLOCK.format(trip.getEndedAt())).append(',')
                        .append(Math.max(0L, Duration.between(trip.getStartedAt(), until).toMinutes())).append(',')
                        .append(trip.getStatus() == TripStatus.ACTIVE ? "Active" : "Ended").append(',')
                        .append(trip.getEndReason() == null ? "" : trip.getEndReason().label()).append(',')
                        .append(km(trip.recordedDistanceM())).append(',')
                        .append(trip.getPlannedDistanceM() == null ? "" : km(trip.getPlannedDistanceM())).append(',')
                        .append(trip.getAdjustedDistanceM() == null ? "" : km(trip.getAdjustedDistanceM()))
                        .append(',')
                        .append(km(trip.payableDistanceM())).append(',')
                        .append(trip.getRatePaise() == null ? "" : rupees(trip.getRatePaise())).append(',')
                        .append(amount == null ? "" : rupees(amount)).append(',')
                        .append(cell(flagText(trip))).append(',')
                        .append(cell(trip.getAdjustedBy() == null
                                ? null
                                : TripViews.displayName(adjusters.get(trip.getAdjustedBy())))).append(',')
                        .append(cell(trip.getAdjustReason()))
                        .append("\r\n");
            }
        }
    }

    private Range range(LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now(clock.withZone(IST));
        LocalDate start = from == null ? today : from;
        LocalDate end = to == null ? today : to;
        if (end.isBefore(start)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "From date must be on or before the to date");
        }
        if (ChronoUnit.DAYS.between(start, end) > MAX_RANGE_DAYS - 1L) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Date range must be 93 days or less");
        }
        return new Range(start, end);
    }

    private Optional<BusinessSettings> business() {
        UUID companyId = CompanyContext.get();
        return companyId != null ? businessRepo.findByCompanyId(companyId) : businessRepo.findById((short) 1);
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value, E fallback, String field) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Unknown " + field + ": " + value);
        }
    }

    static VehicleType effectiveVehicle(JobTrip trip, User technician) {
        return trip.getVehicleType() != null ? trip.getVehicleType() : TripService.vehicleOf(technician);
    }

    private static LocalDate indiaDate(Instant instant) {
        return instant.atZone(IST).toLocalDate();
    }

    private static String sortName(User technician) {
        String name = TripViews.displayName(technician);
        return name == null ? "" : name.toLowerCase(Locale.ROOT);
    }

    private static String fromText(JobTrip trip, Map<UUID, Job> jobsById) {
        return switch (trip.getStartKind()) {
            case SHOP -> "Shop";
            case PREVIOUS_JOB -> {
                Job previous = trip.getStartJobId() == null ? null : jobsById.get(trip.getStartJobId());
                yield previous == null ? "Previous job" : previous.getReferenceNumber();
            }
            case OTHER -> "Other location";
            case UNKNOWN -> "Unknown";
        };
    }

    private static String flagText(JobTrip trip) {
        List<String> labels = new ArrayList<>();
        for (String name : trip.flagNames()) {
            try {
                labels.add(TripFlag.valueOf(name).label());
            } catch (IllegalArgumentException ex) {
                labels.add(name);
            }
        }
        return String.join("; ", labels);
    }

    private static String vehicleText(VehicleType vehicle) {
        if (vehicle == null) {
            return "";
        }
        return vehicle == VehicleType.FOUR_WHEELER ? "Four-wheeler" : "Two-wheeler";
    }

    private static String km(long metres) {
        return String.format(Locale.ROOT, "%.1f", metres / 1000d);
    }

    private static String rupees(long paise) {
        return String.format(Locale.ROOT, "%.2f", paise / 100d);
    }

    private static String cell(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        if (value.indexOf(',') >= 0 || value.indexOf('"') >= 0 || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private record Range(LocalDate from, LocalDate to) {
    }

    /** Trips (ENDED and ACTIVE) and no-trip jobs for one technician in one day or month. */
    private static final class Bucket {
        private final UUID technicianId;
        private final User technician;
        private final LocalDate date;
        private final YearMonth month;
        private final List<JobTrip> trips = new ArrayList<>();
        private final Set<UUID> noTripJobIds = new LinkedHashSet<>();

        private Bucket(UUID technicianId, User technician, LocalDate date, YearMonth month) {
            this.technicianId = technicianId;
            this.technician = technician;
            this.date = date;
            this.month = month;
        }

        private LocalDate periodStart() {
            return month != null ? month.atDay(1) : date;
        }

        private MileageRow row() {
            int ended = 0;
            int active = 0;
            int flagged = 0;
            long tracked = 0;
            long adjustment = 0;
            long amount = 0;
            Set<Integer> rates = new HashSet<>();
            Set<VehicleType> vehicles = EnumSet.noneOf(VehicleType.class);
            for (JobTrip trip : trips) {
                vehicles.add(effectiveVehicle(trip, technician));
                if (trip.getStatus() == TripStatus.ACTIVE) {
                    active++;
                    continue;
                }
                ended++;
                tracked += trip.recordedDistanceM();
                if (trip.getAdjustedDistanceM() != null) {
                    adjustment += trip.getAdjustedDistanceM() - trip.recordedDistanceM();
                }
                Integer tripAmount = trip.amountPaise();
                if (tripAmount != null) {
                    amount += tripAmount;
                }
                rates.add(trip.getRatePaise());
                if (!trip.flagNames().isEmpty()) {
                    flagged++;
                }
            }
            Integer rate = rates.size() == 1 ? rates.iterator().next() : null;
            VehicleType vehicle;
            if (trips.isEmpty()) {
                vehicle = TripService.vehicleOf(technician);
            } else {
                vehicle = vehicles.size() == 1 ? vehicles.iterator().next() : null;
            }
            return new MileageRow(
                    month == null ? date : null,
                    month == null ? null : month.toString(),
                    technicianId,
                    TripViews.displayName(technician),
                    vehicle,
                    ended,
                    tracked,
                    adjustment,
                    tracked + adjustment,
                    rate,
                    amount,
                    flagged,
                    noTripJobIds.size(),
                    active);
        }
    }
}
