package com.acetelecom.repairdesk.feature.request.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import com.acetelecom.repairdesk.feature.request.domain.WarrantyStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateRequestDto(

        @NotNull
        UUID deviceCategoryId,

        @NotBlank @Size(max = 100)
        String contactName,

        @NotBlank @Pattern(regexp = "^\\+?[0-9 \\-]{8,15}$")
        String contactMobile,

        @Size(max = 255)
        String contactEmail,

        @Size(max = 100) String deviceBrand,
        @Size(max = 100) String deviceModel,
        @Size(max = 100) String deviceSerial,

        LocalDate purchaseDate,
        WarrantyStatus warrantyStatus,

        @NotBlank
        String issueDescription,

        @NotNull
        LocalDate preferredDate,

        @NotNull
        LocalTime preferredTime,

        UUID customerId,

        Boolean approveOnCreate,

        @Size(max = 20)
        String priority,

        @Size(max = 2000)
        String note,

        @Size(max = 20)
        String intakeChannel) {

    public boolean shouldApproveOnCreate() {
        return Boolean.TRUE.equals(approveOnCreate);
    }
}
