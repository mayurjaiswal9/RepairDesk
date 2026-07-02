package com.acetelecom.repairdesk.feature.scheduling.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import com.acetelecom.repairdesk.feature.scheduling.domain.Appointment;
import com.acetelecom.repairdesk.feature.scheduling.domain.AppointmentStatus;

public record AppointmentResponse(
        UUID id,
        UUID jobId,
        String referenceNumber,
        UUID technicianId,
        String technicianName,
        String customerName,
        String deviceCategoryName,
        LocalDate scheduledDate,
        LocalTime startTime,
        LocalTime endTime,
        AppointmentStatus status,
        String appointmentNotes,
        Instant createdAt,
        long version) {

    public static AppointmentResponse from(Appointment a) {
        return new AppointmentResponse(
                a.getId(),
                a.getJob().getId(),
                a.getJob().getReferenceNumber(),
                a.getTechnician().getId(),
                a.getTechnician().getName(),
                a.getJob().getContactName(),
                a.getJob().getDeviceCategory().getName(),
                a.getScheduledDate(),
                a.getStartTime(),
                a.getEndTime(),
                a.getStatus(),
                a.getAppointmentNotes(),
                a.getCreatedAt(),
                a.getVersion());
    }
}
