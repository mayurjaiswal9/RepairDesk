package com.acetelecom.repairdesk.feature.location.service;

import org.springframework.stereotype.Component;

import com.acetelecom.repairdesk.common.error.ApiException;
import com.acetelecom.repairdesk.common.error.ErrorCode;
import com.acetelecom.repairdesk.feature.location.domain.PinSource;
import com.acetelecom.repairdesk.feature.location.domain.ServiceLocation;
import com.acetelecom.repairdesk.feature.location.dto.ServiceLocationDto;
import com.acetelecom.repairdesk.feature.location.service.MapsService.GeocodeResult;
import com.acetelecom.repairdesk.feature.location.service.MapsService.Status;

/**
 * Copies a client address onto an entity. Coordinates sent by the client are
 * kept. An address without coordinates is geocoded, and a maps failure leaves
 * the text in place so creating a request never depends on Google.
 */
@Component
public class ServiceLocationApplier {

    private final MapsService mapsService;

    public ServiceLocationApplier(MapsService mapsService) {
        this.mapsService = mapsService;
    }

    public void apply(ServiceLocation target, ServiceLocationDto dto) {
        if (dto == null || !dto.hasContent()) {
            return;
        }
        if ((dto.latitude() == null) != (dto.longitude() == null)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Latitude and longitude must be sent together");
        }
        target.setAddressLine(blankToNull(dto.addressLine()));
        target.setLandmark(blankToNull(dto.landmark()));
        target.setCity(blankToNull(dto.city()));
        target.setPincode(blankToNull(dto.pincode()));

        if (dto.latitude() != null) {
            target.setLatitude(dto.latitude());
            target.setLongitude(dto.longitude());
            target.setPinSource(dto.resolvedPinSource());
            return;
        }

        GeocodeResult result = mapsService.geocode(format(target));
        if (result.status() == Status.FOUND) {
            target.setLatitude(result.latitude());
            target.setLongitude(result.longitude());
            target.setPinSource(PinSource.GEOCODED);
            return;
        }
        target.setLatitude(null);
        target.setLongitude(null);
        target.setPinSource(null);
    }

    static String format(ServiceLocation loc) {
        StringBuilder sb = new StringBuilder();
        append(sb, loc.getAddressLine());
        append(sb, loc.getLandmark());
        append(sb, loc.getCity());
        append(sb, loc.getPincode());
        return sb.toString();
    }

    private static void append(StringBuilder sb, String part) {
        if (part == null || part.isBlank()) {
            return;
        }
        if (!sb.isEmpty()) {
            sb.append(", ");
        }
        sb.append(part.trim());
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
