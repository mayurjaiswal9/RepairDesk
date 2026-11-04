package com.acetelecom.repairdesk.feature.request.dto;

import java.time.LocalDate;
import java.time.LocalTime;

import com.acetelecom.repairdesk.feature.location.dto.ServiceLocationDto;
import com.acetelecom.repairdesk.feature.request.domain.WarrantyStatus;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateRequestDto(

        @NotBlank @Size(max = 100)
        String contactName,

        @NotBlank @Pattern(regexp = "^\\+?[0-9 \\-]{8,15}$")
        String contactMobile,

        @Size(max = 255) String contactEmail,
        @Size(max = 100) String deviceBrand,
        @Size(max = 100) String deviceModel,
        @Size(max = 100) String deviceSerial,

        WarrantyStatus warrantyStatus,

        @NotBlank
        String issueDescription,

        @NotNull LocalDate preferredDate,
        @NotNull LocalTime preferredTime,

        @NotNull Long version,

        @Valid
        ServiceLocationDto serviceLocation) {
}
