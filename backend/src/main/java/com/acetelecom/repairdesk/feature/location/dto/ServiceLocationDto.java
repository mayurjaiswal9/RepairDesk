package com.acetelecom.repairdesk.feature.location.dto;

import com.acetelecom.repairdesk.feature.location.domain.PinSource;
import com.acetelecom.repairdesk.feature.location.domain.ServiceLocation;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Service address as sent by the app and returned on request and job detail.
 * {@code pinSource} is optional. When coordinates are sent without it, the
 * server records {@code MANUAL_PIN}. {@code CHECK_IN} is not accepted from clients.
 */
public record ServiceLocationDto(
        @Size(max = 300) String addressLine,
        @Size(max = 150) String landmark,
        @Size(max = 100) String city,
        @Pattern(regexp = "^[1-9][0-9]{5}$") String pincode,
        @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
        @Pattern(regexp = "GEOCODED|MANUAL_PIN|CUSTOMER_LINK") String pinSource) {

    public boolean hasContent() {
        return notBlank(addressLine) || notBlank(landmark) || notBlank(city) || notBlank(pincode)
                || latitude != null || longitude != null;
    }

    public static ServiceLocationDto from(ServiceLocation loc) {
        if (loc == null || loc.isBlank()) {
            return null;
        }
        return new ServiceLocationDto(
                loc.getAddressLine(),
                loc.getLandmark(),
                loc.getCity(),
                loc.getPincode(),
                loc.getLatitude(),
                loc.getLongitude(),
                loc.getPinSource() == null ? null : loc.getPinSource().name());
    }

    public PinSource resolvedPinSource() {
        if (pinSource == null || pinSource.isBlank()) {
            return PinSource.MANUAL_PIN;
        }
        return PinSource.valueOf(pinSource);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
