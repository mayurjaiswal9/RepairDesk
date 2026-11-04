package com.acetelecom.repairdesk.feature.location.service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acetelecom.repairdesk.common.error.ApiException;
import com.acetelecom.repairdesk.common.error.ErrorCode;
import com.acetelecom.repairdesk.feature.company.CompanyContext;
import com.acetelecom.repairdesk.feature.location.domain.AttendanceDay;
import com.acetelecom.repairdesk.feature.location.domain.EndReason;
import com.acetelecom.repairdesk.feature.location.domain.LocationFlag;
import com.acetelecom.repairdesk.feature.location.domain.LocationSettings;
import com.acetelecom.repairdesk.feature.location.dto.AttendanceRowResponse;
import com.acetelecom.repairdesk.feature.location.dto.MyAttendanceResponse;
import com.acetelecom.repairdesk.feature.location.repository.AttendanceDayRepository;
import com.acetelecom.repairdesk.feature.location.repository.LocationSettingsRepository;
import com.acetelecom.repairdesk.feature.location.service.AttendanceClassifier.Fix;
import com.acetelecom.repairdesk.feature.location.service.AttendanceClassifier.Outcome;
import com.acetelecom.repairdesk.feature.settings.domain.BusinessSettings;
import com.acetelecom.repairdesk.feature.settings.repository.BusinessSettingsRepository;
import com.acetelecom.repairdesk.feature.user.domain.Role;
import com.acetelecom.repairdesk.feature.user.domain.User;
import com.acetelecom.repairdesk.feature.user.repository.UserRepository;

/**
 * Work-day attendance. Opening a job, starting a repair, and presence pings
 * do not call this class. Auto-end closes the day only. It does not change a job.
 */
@Service
public class AttendanceService {

    static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    static final int MAX_RANGE_DAYS = 31;

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm").withZone(IST);

    private final AttendanceDayRepository days;
    private final LocationSettingsRepository locationRepo;
    private final BusinessSettingsRepository businessRepo;
    private final UserRepository users;
    private final Clock clock;

    public AttendanceService(AttendanceDayRepository days, LocationSettingsRepository locationRepo,
            BusinessSettingsRepository businessRepo, UserRepository users, Clock clock) {
        this.days = days;
        this.locationRepo = locationRepo;
        this.businessRepo = businessRepo;
        this.users = users;
        this.clock = clock;
    }

    /** Days closed by this run, and the stored auto-end clock used in the notice. */
    public record AutoEndResult(LocalTime autoEndTime, List<UUID> technicianIds) {
    }

    @Transactional
    public MyAttendanceResponse start(UUID technicianId, Fix fix) {
        LocationSettings settings = requireLocation();
        if (!settings.isAttendanceEnabled()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Attendance is not turned on");
        }
        LocalDate today = today();
        var existing = days.findByTechnicianIdAndWorkDate(technicianId, today);
        if (existing.isPresent()) {
            return toMine(existing.get());
        }
        BusinessSettings business = requireBusiness();
        Outcome outcome = AttendanceClassifier.classify(fix, business.getShopLat(), business.getShopLng(),
                business.getGeofenceRadiusM(), settings.isAllowStartOutsideGeofence());
        if (!outcome.allowed()) {
            int metres = outcome.distanceMetres() == null ? 0 : outcome.distanceMetres();
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "You are " + metres + " m from the shop. Start day was not saved.",
                    Map.of("distanceMetres", metres));
        }
        Instant now = clock.instant();
        AttendanceDay day = new AttendanceDay();
        day.setTechnicianId(technicianId);
        day.setWorkDate(today);
        day.setStartAt(now);
        day.setStartFlag(outcome.flag());
        day.setPrivacyNoticeVersion(settings.getPrivacyNoticeVersion());
        day.setUpdatedAt(now);
        applyPoint(fix, true, day);
        days.save(day);
        return toMine(day);
    }

    @Transactional
    public MyAttendanceResponse end(UUID technicianId, Fix fix) {
        LocationSettings settings = requireLocation();
        if (!settings.isAttendanceEnabled()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Attendance is not turned on");
        }
        AttendanceDay day = days.findByTechnicianIdAndWorkDate(technicianId, today())
                .orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_ERROR, "No day is open."));
        if (day.getEndAt() != null) {
            return toMine(day);
        }
        BusinessSettings business = requireBusiness();
        Outcome outcome = AttendanceClassifier.classify(fix, business.getShopLat(), business.getShopLng(),
                business.getGeofenceRadiusM(), true);
        Instant now = clock.instant();
        day.setEndAt(now);
        day.setEndFlag(outcome.flag());
        day.setEndReason(EndReason.MANUAL);
        day.setUpdatedAt(now);
        applyPoint(fix, false, day);
        days.save(day);
        return toMine(day);
    }

    @Transactional(readOnly = true)
    public MyAttendanceResponse today(UUID technicianId) {
        LocalDate today = today();
        return days.findByTechnicianIdAndWorkDate(technicianId, today)
                .map(AttendanceService::toMine)
                .orElseGet(() -> new MyAttendanceResponse(false, today, null, null, null, null, null));
    }

    @Transactional(readOnly = true)
    public List<AttendanceRowResponse> list(LocalDate from, LocalDate to) {
        return list(from, to, null);
    }

    @Transactional(readOnly = true)
    public List<AttendanceRowResponse> list(LocalDate from, LocalDate to, UUID technicianId) {
        Range range = range(from, to);
        return rows(range.from(), range.to(), technicianId);
    }

    @Transactional(readOnly = true)
    public byte[] csv(LocalDate from, LocalDate to) {
        return csv(from, to, null);
    }

    @Transactional(readOnly = true)
    public byte[] csv(LocalDate from, LocalDate to, UUID technicianId) {
        Range range = range(from, to);
        StringBuilder csv = new StringBuilder();
        csv.append("Name,Date,Status,Start flag,End time,End,Hours\r\n");
        for (AttendanceRowResponse row : rows(range.from(), range.to(), technicianId)) {
            csv.append(cell(row.technicianName())).append(',')
                    .append(row.workDate().format(DATE)).append(',')
                    .append(cell(statusText(row))).append(',')
                    .append(cell(flagText(row.startFlag()))).append(',')
                    .append(row.endAt() == null ? "" : CLOCK.format(row.endAt())).append(',')
                    .append(cell(reasonText(row.endReason()))).append(',')
                    .append(hours(row.durationMinutes()))
                    .append("\r\n");
        }
        byte[] text = csv.toString().getBytes(StandardCharsets.UTF_8);
        byte[] body = new byte[text.length + 3];
        body[0] = (byte) 0xEF;
        body[1] = (byte) 0xBB;
        body[2] = (byte) 0xBF;
        System.arraycopy(text, 0, body, 3, text.length);
        return body;
    }

    /**
     * Closes open days whose India auto-end time has passed, including a day
     * left open from an earlier date after a cold start. Does not change a job.
     * Still runs when the attendance switch is off, so a day already started can close.
     */
    @Transactional
    public AutoEndResult autoEndOpenDays() {
        LocationSettings settings = locationRepo.findById((short) 1).orElse(null);
        if (settings == null) {
            return new AutoEndResult(LocalTime.of(20, 0), List.of());
        }
        LocalDate today = today();
        LocalTime now = LocalTime.now(clock.withZone(IST));
        LocalTime autoEnd = settings.getDayAutoEndTime();
        BusinessSettings business = businessRepo.findById((short) 1).orElse(null);
        if (business != null && !business.isAutoEndDay()) {
            return new AutoEndResult(autoEnd, List.of());
        }
        Instant instant = clock.instant();
        List<UUID> closed = new ArrayList<>();
        for (AttendanceDay day : days.findByEndAtIsNull()) {
            if (day.getWorkDate().isAfter(today)) {
                continue;
            }
            boolean due = day.getWorkDate().isBefore(today) || !now.isBefore(autoEnd);
            if (!due) {
                continue;
            }
            day.setEndAt(instant);
            day.setEndReason(EndReason.AUTO);
            day.setEndFlag(null);
            day.setEndLat(null);
            day.setEndLng(null);
            day.setEndAccuracyM(null);
            day.setUpdatedAt(instant);
            days.save(day);
            closed.add(day.getTechnicianId());
        }
        return new AutoEndResult(autoEnd, List.copyOf(closed));
    }

    private List<AttendanceRowResponse> rows(LocalDate from, LocalDate to, UUID technicianId) {
        List<User> technicians = techniciansForList(technicianId);
        Map<String, AttendanceDay> byKey = new HashMap<>();
        for (AttendanceDay day : days.findByWorkDateBetween(from, to)) {
            byKey.put(day.getTechnicianId() + "|" + day.getWorkDate(), day);
        }
        List<AttendanceRowResponse> rows = new ArrayList<>();
        for (LocalDate date = to; !date.isBefore(from); date = date.minusDays(1)) {
            for (User technician : technicians) {
                AttendanceDay day = byKey.get(technician.getId() + "|" + date);
                rows.add(toRow(technician, date, day));
            }
        }
        return rows;
    }

    private List<User> techniciansForList(UUID technicianId) {
        List<User> technicians = users.findActiveByRole(Role.TECHNICIAN).stream()
                .filter(User::isActive)
                .toList();
        if (technicianId == null) {
            return technicians;
        }
        for (User technician : technicians) {
            if (technician.getId().equals(technicianId)) {
                return List.of(technician);
            }
        }
        User one = users.findById(technicianId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Technician not found"));
        CompanyContext.requireSame(one.getCompanyId(), "Technician not found");
        if (one.getRole() != Role.TECHNICIAN) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Technician not found");
        }
        return List.of(one);
    }

    private Range range(LocalDate from, LocalDate to) {
        LocalDate today = today();
        LocalDate start = from == null ? today : from;
        LocalDate end = to == null ? today : to;
        if (end.isBefore(start)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "From date must be on or before the to date");
        }
        if (ChronoUnit.DAYS.between(start, end) > MAX_RANGE_DAYS - 1L) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Date range must be 31 days or less");
        }
        return new Range(start, end);
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(IST));
    }

    private LocationSettings requireLocation() {
        UUID companyId = CompanyContext.get();
        if (companyId != null) {
            return locationRepo.findByCompanyId(companyId)
                    .orElseThrow(() -> new ApiException(ErrorCode.INTERNAL_ERROR, "Location settings not initialised"));
        }
        return locationRepo.findById((short) 1)
                .orElseThrow(() -> new ApiException(ErrorCode.INTERNAL_ERROR, "Location settings not initialised"));
    }

    private BusinessSettings requireBusiness() {
        UUID companyId = CompanyContext.get();
        if (companyId != null) {
            return businessRepo.findByCompanyId(companyId)
                    .orElseThrow(() -> new ApiException(ErrorCode.INTERNAL_ERROR, "Business settings not initialised"));
        }
        return businessRepo.findById((short) 1)
                .orElseThrow(() -> new ApiException(ErrorCode.INTERNAL_ERROR, "Business settings not initialised"));
    }

    private static void applyPoint(Fix fix, boolean start, AttendanceDay day) {
        if (fix == null || fix.latitude() == null || fix.longitude() == null) {
            return;
        }
        if (start) {
            day.setStartLat(fix.latitude());
            day.setStartLng(fix.longitude());
            day.setStartAccuracyM(fix.accuracyM());
        } else {
            day.setEndLat(fix.latitude());
            day.setEndLng(fix.longitude());
            day.setEndAccuracyM(fix.accuracyM());
        }
    }

    private static MyAttendanceResponse toMine(AttendanceDay day) {
        return new MyAttendanceResponse(
                true,
                day.getWorkDate(),
                day.getStartAt(),
                day.getEndAt(),
                name(day.getStartFlag()),
                name(day.getEndFlag()),
                day.getEndReason() == null ? null : day.getEndReason().name());
    }

    private static AttendanceRowResponse toRow(User technician, LocalDate date, AttendanceDay day) {
        if (day == null) {
            return new AttendanceRowResponse(technician.getId(), displayName(technician), date,
                    "NOT_STARTED", null, null, null, null, null);
        }
        String status = day.getEndAt() == null ? "ON_DUTY"
                : day.getEndReason() == EndReason.AUTO ? "AUTO_ENDED" : "ENDED";
        Long minutes = day.getEndAt() == null ? null : Duration.between(day.getStartAt(), day.getEndAt()).toMinutes();
        if (minutes != null && minutes < 0) {
            minutes = null;
        }
        return new AttendanceRowResponse(
                technician.getId(),
                displayName(technician),
                date,
                status,
                day.getStartAt(),
                day.getEndAt(),
                name(day.getStartFlag()),
                day.getEndReason() == null ? null : day.getEndReason().name(),
                minutes);
    }

    private static String displayName(User technician) {
        if (technician.getName() != null && !technician.getName().isBlank()) {
            return technician.getName();
        }
        return technician.getUsername();
    }

    private static String statusText(AttendanceRowResponse row) {
        return switch (row.status()) {
            case "ON_DUTY" -> "On duty since " + CLOCK.format(row.startAt());
            case "ENDED" -> "Ended";
            case "AUTO_ENDED" -> "Auto-ended";
            default -> "Not started";
        };
    }

    private static String flagText(String flag) {
        if (flag == null) {
            return "";
        }
        return LocationFlag.valueOf(flag).label();
    }

    private static String reasonText(String reason) {
        if (reason == null) {
            return "";
        }
        return EndReason.valueOf(reason).label();
    }

    private static String hours(Long minutes) {
        if (minutes == null) {
            return "";
        }
        return (minutes / 60) + "h " + String.format("%02d", minutes % 60) + "m";
    }

    private static String cell(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        if (value.indexOf(',') >= 0 || value.indexOf('"') >= 0 || value.indexOf('\n') >= 0) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private static String name(LocationFlag flag) {
        return flag == null ? null : flag.name();
    }

    private record Range(LocalDate from, LocalDate to) {
    }
}
