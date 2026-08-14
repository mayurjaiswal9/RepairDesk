package com.acetelecom.repairdesk.feature.devicecategory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DeviceCategoryRequest(

        @NotBlank
        @Size(max = 100)
        String name,

        @Size(max = 500)
        String description,

        /** "CONFIGURED" or "FREE_TEXT". Optional; defaults to FREE_TEXT when absent/blank. */
        String brandMode) {
}
