package com.acetelecom.repairdesk.feature.devicecategory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DeviceBrandRequest(

        @NotBlank
        @Size(max = 100)
        String name) {
}
