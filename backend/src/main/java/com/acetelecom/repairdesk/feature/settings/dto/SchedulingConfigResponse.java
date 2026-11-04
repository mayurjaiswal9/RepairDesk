package com.acetelecom.repairdesk.feature.settings.dto;

import java.time.LocalTime;
import java.util.List;

public record SchedulingConfigResponse(
        int slotDurationMinutes,
        int bookingHorizonDays,
        boolean autoEndDay,
        List<WorkingHoursDto> workingHours) {

    public record WorkingHoursDto(
            int dayOfWeek,
            boolean isClosed,
            LocalTime openTime,
            LocalTime closeTime) {
    }
}
