package com.acetelecom.repairdesk.feature.location.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.acetelecom.repairdesk.common.error.ApiException;
import com.acetelecom.repairdesk.feature.location.domain.AttendanceDay;
import com.acetelecom.repairdesk.feature.location.domain.EndReason;
import com.acetelecom.repairdesk.feature.location.domain.LocationFlag;
import com.acetelecom.repairdesk.feature.location.domain.LocationSettings;
import com.acetelecom.repairdesk.feature.location.dto.AttendanceRowResponse;
import com.acetelecom.repairdesk.feature.location.repository.AttendanceDayRepository;
import com.acetelecom.repairdesk.feature.location.repository.LocationSettingsRepository;
import com.acetelecom.repairdesk.feature.location.service.AttendanceClassifier.Fix;
import com.acetelecom.repairdesk.feature.settings.domain.BusinessSettings;
import com.acetelecom.repairdesk.feature.settings.repository.BusinessSettingsRepository;
import com.acetelecom.repairdesk.feature.user.domain.AccountStatus;
import com.acetelecom.repairdesk.feature.user.domain.Role;
import com.acetelecom.repairdesk.feature.user.domain.User;
import com.acetelecom.repairdesk.feature.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class AttendanceServiceTest {

    private static final double SHOP_LAT = 19.1412127;
    private static final double SHOP_LNG = 72.8309064;
    private static final Instant MORNING = Instant.parse("2026-10-18T04:20:00Z");
    private static final LocalDate INDIA_DAY = LocalDate.of(2026, 10, 18);

    @Mock private AttendanceDayRepository days;
    @Mock private LocationSettingsRepository locationRepo;
    @Mock private BusinessSettingsRepository businessRepo;
    @Mock private UserRepository users;

    private LocationSettings settings;
    private BusinessSettings business;
    private AttendanceService service;
    private UUID technicianId;

    @BeforeEach
    void setUp() {
        settings = new LocationSettings();
        settings.setAttendanceEnabled(true);
        settings.setAllowStartOutsideGeofence(false);
        settings.setDayAutoEndTime(LocalTime.of(19, 0));
        settings.setPrivacyNoticeVersion("1");
        business = new BusinessSettings();
        business.setShopLat(SHOP_LAT);
        business.setShopLng(SHOP_LNG);
        business.setGeofenceRadiusM(150);
        technicianId = UUID.randomUUID();
        service = serviceAt(MORNING);
    }

    @Test
    void trustworthyFixOutsideTheShopDoesNotSaveTheDay() {
        stubSettings();
        when(days.findByTechnicianIdAndWorkDate(technicianId, INDIA_DAY)).thenReturn(Optional.empty());
        Fix fix = new Fix(SHOP_LAT + 0.004, SHOP_LNG, 12d, false);

        assertThatThrownBy(() -> service.start(technicianId, fix))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("m from the shop. Start day was not saved.")
                .hasMessageContaining("Start day was not saved");

        verify(days, never()).save(any());
    }

    @Test
    void missingFixStartsTheDayAndKeepsTheJobUntouched() {
        stubSettings();
        when(days.findByTechnicianIdAndWorkDate(technicianId, INDIA_DAY)).thenReturn(Optional.empty());
        when(days.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.start(technicianId, null);

        ArgumentCaptor<AttendanceDay> saved = ArgumentCaptor.forClass(AttendanceDay.class);
        verify(days).save(saved.capture());
        assertThat(saved.getValue().getStartFlag()).isEqualTo(LocationFlag.MISSING);
        assertThat(saved.getValue().getStartLat()).isNull();
        assertThat(saved.getValue().getPrivacyNoticeVersion()).isEqualTo("1");
        assertThat(response.started()).isTrue();
        assertThat(response.startFlag()).isEqualTo("MISSING");
    }

    @Test
    void secondStartOnTheSameIndiaDateReturnsTheExistingDay() {
        stubLocationOnly();
        AttendanceDay existing = openDay(INDIA_DAY, MORNING.minusSeconds(600));
        when(days.findByTechnicianIdAndWorkDate(technicianId, INDIA_DAY)).thenReturn(Optional.of(existing));

        var response = service.start(technicianId, new Fix(SHOP_LAT, SHOP_LNG, 10d, false));

        assertThat(response.startAt()).isEqualTo(existing.getStartAt());
        verify(days, never()).save(any());
    }

    @Test
    void attendanceOffWritesNothingOnStartOrEnd() {
        settings.setAttendanceEnabled(false);
        stubLocationOnly();

        assertThatThrownBy(() -> service.start(technicianId, null))
                .isInstanceOf(ApiException.class)
                .hasMessage("Attendance is not turned on");
        assertThatThrownBy(() -> service.end(technicianId, null))
                .isInstanceOf(ApiException.class)
                .hasMessage("Attendance is not turned on");
        verify(days, never()).save(any());
        verify(days, never()).findByTechnicianIdAndWorkDate(any(), any());
    }

    @Test
    void endIsAllowedWhenTheFixIsMissingOrOutside() {
        stubSettings();
        AttendanceDay open = openDay(INDIA_DAY, MORNING.minusSeconds(3600));
        when(days.findByTechnicianIdAndWorkDate(technicianId, INDIA_DAY)).thenReturn(Optional.of(open));
        when(days.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var missing = service.end(technicianId, null);
        assertThat(missing.endFlag()).isEqualTo("MISSING");
        assertThat(missing.endReason()).isEqualTo("MANUAL");

        open.setEndAt(null);
        open.setEndFlag(null);
        open.setEndReason(null);
        var outside = service.end(technicianId, new Fix(SHOP_LAT + 0.004, SHOP_LNG, 10d, false));
        assertThat(outside.endFlag()).isEqualTo("OUTSIDE");
        assertThat(outside.endReason()).isEqualTo("MANUAL");
    }

    @Test
    void endWithoutAnOpenDayIsRefused() {
        stubLocationOnly();
        when(days.findByTechnicianIdAndWorkDate(technicianId, INDIA_DAY)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.end(technicianId, null))
                .isInstanceOf(ApiException.class)
                .hasMessage("No day is open.");
        verify(days, never()).save(any());
    }

    @Test
    void listIncludesTechniciansWithNoRowAndOmitsCoordinates() {
        User active = technician("Asha");
        User other = technician("Bina");
        User disabled = technician("Closed");
        disabled.setStatus(AccountStatus.DISABLED);
        AttendanceDay day = openDay(INDIA_DAY, MORNING);
        day.setTechnicianId(active.getId());
        day.setStartLat(SHOP_LAT);
        day.setStartLng(SHOP_LNG);
        when(days.findByWorkDateBetween(INDIA_DAY, INDIA_DAY)).thenReturn(List.of(day));
        when(users.findActiveByRole(Role.TECHNICIAN)).thenReturn(List.of(active, other, disabled));

        List<AttendanceRowResponse> rows = service.list(null, null);

        assertThat(rows).extracting(AttendanceRowResponse::technicianName).containsExactly("Asha", "Bina");
        assertThat(rows.get(0).status()).isEqualTo("ON_DUTY");
        assertThat(rows.get(1).status()).isEqualTo("NOT_STARTED");
        assertThat(rows.get(0).startFlag()).isEqualTo("INSIDE");
        String csv = new String(service.csv(INDIA_DAY, INDIA_DAY), StandardCharsets.UTF_8);
        assertThat(csv).doesNotContain("19.141");
        assertThat(csv).contains("On duty since 09:50");
        assertThat(csv).contains("Not started");
        assertThat(csv.charAt(0)).isEqualTo('\uFEFF');
    }

    @Test
    void listCanFilterToOneTechnician() {
        User asha = technician("Asha");
        User bina = technician("Bina");
        when(users.findActiveByRole(Role.TECHNICIAN)).thenReturn(List.of(asha, bina));
        when(days.findByWorkDateBetween(INDIA_DAY, INDIA_DAY)).thenReturn(List.of());

        List<AttendanceRowResponse> rows = service.list(INDIA_DAY, INDIA_DAY, asha.getId());

        assertThat(rows).extracting(AttendanceRowResponse::technicianName).containsExactly("Asha");
    }

    @Test
    void rangeLongerThanThirtyOneDaysIsRefused() {
        assertThatThrownBy(() -> service.list(INDIA_DAY.minusDays(31), INDIA_DAY))
                .isInstanceOf(ApiException.class)
                .hasMessage("Date range must be 31 days or less");
    }

    @Test
    void autoEndClosesOpenDaysWithoutAnEndFixAndLeavesAFutureDayOpen() {
        service = serviceAt(Instant.parse("2026-10-18T13:30:00Z"));
        when(locationRepo.findById((short) 1)).thenReturn(Optional.of(settings));
        AttendanceDay today = openDay(INDIA_DAY, MORNING);
        AttendanceDay yesterday = openDay(INDIA_DAY.minusDays(1), MORNING.minusSeconds(86_400));
        AttendanceDay tomorrow = openDay(INDIA_DAY.plusDays(1), MORNING);
        when(days.findByEndAtIsNull()).thenReturn(List.of(today, yesterday, tomorrow));
        when(days.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.autoEndOpenDays();

        assertThat(result.technicianIds()).containsExactly(today.getTechnicianId(), yesterday.getTechnicianId());
        assertThat(today.getEndReason()).isEqualTo(EndReason.AUTO);
        assertThat(today.getEndFlag()).isNull();
        assertThat(today.getEndLat()).isNull();
        assertThat(yesterday.getEndReason()).isEqualTo(EndReason.AUTO);
        assertThat(tomorrow.getEndAt()).isNull();
    }

    @Test
    void autoEndDoesNothingWhenTheAdminTurnsItOff() {
        service = serviceAt(Instant.parse("2026-10-18T13:30:00Z"));
        when(locationRepo.findById((short) 1)).thenReturn(Optional.of(settings));
        BusinessSettings off = new BusinessSettings();
        off.setAutoEndDay(false);
        when(businessRepo.findById((short) 1)).thenReturn(Optional.of(off));

        var result = service.autoEndOpenDays();

        assertThat(result.technicianIds()).isEmpty();
        verify(days, never()).findByEndAtIsNull();
    }

    @Test
    void autoEndBeforeTheClockLeavesTodayOpenAndCatchesYesterday() {
        service = serviceAt(Instant.parse("2026-10-18T13:29:00Z"));
        when(locationRepo.findById((short) 1)).thenReturn(Optional.of(settings));
        AttendanceDay today = openDay(INDIA_DAY, MORNING);
        AttendanceDay yesterday = openDay(INDIA_DAY.minusDays(1), MORNING.minusSeconds(86_400));
        when(days.findByEndAtIsNull()).thenReturn(List.of(today, yesterday));
        when(days.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.autoEndOpenDays();

        assertThat(result.technicianIds()).containsExactly(yesterday.getTechnicianId());
        assertThat(today.getEndAt()).isNull();
    }

    private void stubSettings() {
        when(locationRepo.findById((short) 1)).thenReturn(Optional.of(settings));
        when(businessRepo.findById((short) 1)).thenReturn(Optional.of(business));
    }

    private void stubLocationOnly() {
        when(locationRepo.findById((short) 1)).thenReturn(Optional.of(settings));
    }

    private AttendanceService serviceAt(Instant instant) {
        return new AttendanceService(days, locationRepo, businessRepo, users,
                Clock.fixed(instant, ZoneOffset.UTC));
    }

    private AttendanceDay openDay(LocalDate date, Instant start) {
        AttendanceDay day = new AttendanceDay();
        day.setTechnicianId(UUID.randomUUID());
        day.setWorkDate(date);
        day.setStartAt(start);
        day.setStartFlag(LocationFlag.INSIDE);
        day.setPrivacyNoticeVersion("1");
        return day;
    }

    private static User technician(String name) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setName(name);
        user.setUsername(name.toLowerCase());
        user.setRole(Role.TECHNICIAN);
        user.setStatus(AccountStatus.ACTIVE);
        return user;
    }
}
