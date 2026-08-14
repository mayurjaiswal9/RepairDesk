package com.acetelecom.repairdesk.feature.devicecategory.dto;

import java.util.UUID;

import com.acetelecom.repairdesk.feature.devicecategory.domain.DeviceBrand;

public record DeviceBrandResponse(UUID id, UUID deviceCategoryId, String name, boolean archived) {

    public static DeviceBrandResponse from(DeviceBrand b) {
        return new DeviceBrandResponse(
                b.getId(), b.getDeviceCategory().getId(), b.getName(), b.isArchived());
    }
}
