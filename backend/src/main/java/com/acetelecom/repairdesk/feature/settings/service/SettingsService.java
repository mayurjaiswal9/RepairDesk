package com.acetelecom.repairdesk.feature.settings.service;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acetelecom.repairdesk.common.error.ApiException;
import com.acetelecom.repairdesk.common.error.ErrorCode;
import com.acetelecom.repairdesk.feature.company.CompanyContext;
import com.acetelecom.repairdesk.feature.settings.domain.BusinessSettings;
import com.acetelecom.repairdesk.feature.settings.domain.WorkingHours;
import com.acetelecom.repairdesk.feature.settings.dto.SchedulingConfigResponse;
import com.acetelecom.repairdesk.feature.settings.repository.BusinessSettingsRepository;
import com.acetelecom.repairdesk.feature.settings.repository.WorkingHoursRepository;

@Service
@Transactional(readOnly = true)
public class SettingsService {

    private static final Set<Integer> VALID_SLOTS = Set.of(30, 60, 90, 120);

    private final BusinessSettingsRepository settingsRepo;
    private final WorkingHoursRepository workingHoursRepo;

    public SettingsService(BusinessSettingsRepository settingsRepo,
            WorkingHoursRepository workingHoursRepo) {
        this.settingsRepo = settingsRepo;
        this.workingHoursRepo = workingHoursRepo;
    }

    public BusinessSettings getSettings() {
        UUID companyId = CompanyContext.get();
        if (companyId != null) {
            return settingsRepo.findByCompanyId(companyId)
                    .orElseThrow(() -> new ApiException(ErrorCode.INTERNAL_ERROR,
                            "Business settings not initialised"));
        }
        return settingsRepo.findById((short) 1)
                .orElseThrow(() -> new ApiException(ErrorCode.INTERNAL_ERROR,
                        "Business settings not initialised"));
    }

    public SchedulingConfigResponse getSchedulingConfig() {
        BusinessSettings settings = getSettings();
        UUID companyId = CompanyContext.get();
        var source = companyId == null
                ? workingHoursRepo.findAllByOrderByDayOfWeekAsc()
                : workingHoursRepo.findByCompanyIdOrderByDayOfWeekAsc(companyId);
        var hours = source.stream()
                .map(wh -> new SchedulingConfigResponse.WorkingHoursDto(
                        wh.getDayOfWeek(), wh.isClosed(), wh.getOpenTime(), wh.getCloseTime()))
                .toList();
        return new SchedulingConfigResponse(
                settings.getSlotDurationMinutes(),
                settings.getBookingHorizonDays(),
                settings.isAutoEndDay(),
                hours);
    }

    @Transactional
    public BusinessSettings updateSettings(String businessName, String businessAddress,
            String businessPhone, String businessEmail,
            Integer slotDurationMinutes, Integer bookingHorizonDays, Boolean autoEndDay) {
        BusinessSettings s = getSettings();
        if (businessName != null) s.setBusinessName(businessName);
        if (businessAddress != null) s.setBusinessAddress(businessAddress);
        if (businessPhone != null) s.setBusinessPhone(businessPhone);
        if (businessEmail != null) s.setBusinessEmail(businessEmail);
        if (slotDurationMinutes != null) {
            if (!VALID_SLOTS.contains(slotDurationMinutes)) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR,
                        "Slot duration must be one of: 30, 60, 90, 120 minutes");
            }
            s.setSlotDurationMinutes(slotDurationMinutes);
        }
        if (bookingHorizonDays != null) {
            if (bookingHorizonDays < 1 || bookingHorizonDays > 365) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR,
                        "Booking horizon must be between 1 and 365 days");
            }
            s.setBookingHorizonDays(bookingHorizonDays);
        }
        if (autoEndDay != null) {
            s.setAutoEndDay(autoEndDay);
        }
        s.setUpdatedAt(Instant.now());
        return settingsRepo.save(s);
    }

    @Transactional
    public List<WorkingHours> updateWorkingHours(List<WorkingHoursUpdate> updates) {
        var existing = workingHoursRepo.findAllByOrderByDayOfWeekAsc();
        for (WorkingHoursUpdate u : updates) {
            existing.stream()
                    .filter(wh -> wh.getDayOfWeek() == u.dayOfWeek())
                    .findFirst()
                    .ifPresent(wh -> {
                        wh.setClosed(u.isClosed());
                        if (!u.isClosed()) {
                            if (u.openTime() == null || u.closeTime() == null) {
                                throw new ApiException(ErrorCode.VALIDATION_ERROR,
                                        "Open and close times required when day is not closed");
                            }
                            if (!u.closeTime().isAfter(u.openTime())) {
                                throw new ApiException(ErrorCode.VALIDATION_ERROR,
                                        "Close time must be after open time for day "
                                                + u.dayOfWeek());
                            }
                            wh.setOpenTime(u.openTime());
                            wh.setCloseTime(u.closeTime());
                        } else {
                            wh.setOpenTime(null);
                            wh.setCloseTime(null);
                        }
                    });
        }
        return workingHoursRepo.saveAll(existing);
    }

    public record WorkingHoursUpdate(int dayOfWeek, boolean isClosed,
            LocalTime openTime, LocalTime closeTime) {
    }
}
