package com.acetelecom.repairdesk.feature.settings.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acetelecom.repairdesk.common.error.ApiException;
import com.acetelecom.repairdesk.common.error.ErrorCode;
import com.acetelecom.repairdesk.feature.settings.domain.BusinessSettings;
import com.acetelecom.repairdesk.feature.settings.dto.SchedulingConfigResponse;
import com.acetelecom.repairdesk.feature.settings.repository.BusinessSettingsRepository;
import com.acetelecom.repairdesk.feature.settings.repository.WorkingHoursRepository;

@Service
@Transactional(readOnly = true)
public class SettingsService {

    private final BusinessSettingsRepository settingsRepo;
    private final WorkingHoursRepository workingHoursRepo;

    public SettingsService(BusinessSettingsRepository settingsRepo,
            WorkingHoursRepository workingHoursRepo) {
        this.settingsRepo = settingsRepo;
        this.workingHoursRepo = workingHoursRepo;
    }

    public BusinessSettings getSettings() {
        return settingsRepo.findById((short) 1)
                .orElseThrow(() -> new ApiException(ErrorCode.INTERNAL_ERROR,
                        "Business settings not initialised"));
    }

    public SchedulingConfigResponse getSchedulingConfig() {
        BusinessSettings settings = getSettings();
        var hours = workingHoursRepo.findAllByOrderByDayOfWeekAsc().stream()
                .map(wh -> new SchedulingConfigResponse.WorkingHoursDto(
                        wh.getDayOfWeek(), wh.isClosed(), wh.getOpenTime(), wh.getCloseTime()))
                .toList();
        return new SchedulingConfigResponse(
                settings.getSlotDurationMinutes(),
                settings.getBookingHorizonDays(),
                hours);
    }
}
