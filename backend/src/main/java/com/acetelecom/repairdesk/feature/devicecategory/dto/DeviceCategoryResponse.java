package com.acetelecom.repairdesk.feature.devicecategory.dto;

import java.util.UUID;

import com.acetelecom.repairdesk.feature.devicecategory.domain.DeviceCategory;

public record DeviceCategoryResponse(
        UUID id,
        String name,
        String description,
        boolean archived,
        String brandMode,
        long activeBrandCount) {

    public static DeviceCategoryResponse from(DeviceCategory c) {
        return from(c, 0);
    }

    public static DeviceCategoryResponse from(DeviceCategory c, long activeBrandCount) {
        return new DeviceCategoryResponse(
                c.getId(),
                c.getName(),
                c.getDescription(),
                c.isArchived(),
                c.getBrandMode().name(),
                activeBrandCount);
    }
}
