package com.acetelecom.repairdesk.feature.location.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acetelecom.repairdesk.common.error.ApiException;
import com.acetelecom.repairdesk.common.error.ErrorCode;
import com.acetelecom.repairdesk.feature.company.CompanyContext;
import com.acetelecom.repairdesk.feature.location.domain.LocationSettings;
import com.acetelecom.repairdesk.feature.location.dto.LocationConfigResponse;
import com.acetelecom.repairdesk.feature.location.dto.LocationSettingsResponse;
import com.acetelecom.repairdesk.feature.location.dto.UpdateLocationSettingsRequest;
import com.acetelecom.repairdesk.feature.location.repository.LocationSettingsRepository;
import com.acetelecom.repairdesk.feature.settings.domain.BusinessSettings;
import com.acetelecom.repairdesk.feature.settings.repository.BusinessSettingsRepository;

@Service
@Transactional(readOnly = true)
public class LocationSettingsService {

    private final LocationSettingsRepository locationRepo;
    private final BusinessSettingsRepository businessRepo;

    public LocationSettingsService(LocationSettingsRepository locationRepo,
            BusinessSettingsRepository businessRepo) {
        this.locationRepo = locationRepo;
        this.businessRepo = businessRepo;
    }

    public LocationSettingsResponse get() {
        return toResponse(requireLocation(), requireBusiness());
    }

    public LocationConfigResponse config() {
        LocationSettings location = requireLocation();
        BusinessSettings business = requireBusiness();
        return new LocationConfigResponse(
                location.isCheckInEnabled(),
                location.isAttendanceEnabled(),
                location.isTrackingEnabled(),
                location.isTravelEnabled(),
                location.isEtaEnabled(),
                location.getPingIntervalMinutes(),
                location.getPingDistanceM(),
                location.getDayAutoEndTime(),
                location.isAllowStartOutsideGeofence(),
                business.getGeofenceRadiusM(),
                business.getShopLat(),
                business.getShopLng(),
                location.getFarFromAddressM(),
                location.getPrivacyNoticeVersion(),
                location.isStrictTripMode(),
                location.isTripOpenMaps(),
                location.getTravelPingSeconds(),
                location.getOnSitePingMinutes(),
                business.isAutoEndDay());
    }

    @Transactional
    public LocationSettingsResponse update(UpdateLocationSettingsRequest body) {
        if ((body.shopLatitude() == null) != (body.shopLongitude() == null)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Shop latitude and longitude must be set together");
        }
        LocationSettings location = requireLocation();
        BusinessSettings business = requireBusiness();

        business.setShopLat(body.shopLatitude());
        business.setShopLng(body.shopLongitude());
        business.setGeofenceRadiusM(body.geofenceRadiusM());
        business.setUpdatedAt(Instant.now());

        location.setCheckInEnabled(body.checkInEnabled());
        location.setAttendanceEnabled(body.attendanceEnabled());
        location.setTrackingEnabled(body.trackingEnabled());
        location.setTravelEnabled(body.travelEnabled());
        location.setEtaEnabled(body.etaEnabled());
        location.setPingIntervalMinutes(body.pingIntervalMinutes());
        location.setPingDistanceM(body.pingDistanceM());
        location.setDayAutoEndTime(body.dayAutoEndTime());
        location.setAllowStartOutsideGeofence(body.allowStartOutsideGeofence());
        location.setFarFromAddressM(body.farFromAddressM());
        location.setRateTwoWheelerPaise(body.rateTwoWheelerPaise());
        location.setRateFourWheelerPaise(body.rateFourWheelerPaise());
        location.setRetentionDays(body.retentionDays());
        location.setPrivacyNoticeVersion(body.privacyNoticeVersion().trim());
        if (body.strictTripMode() != null) {
            location.setStrictTripMode(body.strictTripMode());
        }
        if (body.tripOpenMaps() != null) {
            location.setTripOpenMaps(body.tripOpenMaps());
        }
        if (body.travelPingSeconds() != null) {
            location.setTravelPingSeconds(body.travelPingSeconds());
        }
        if (body.onSitePingMinutes() != null) {
            location.setOnSitePingMinutes(body.onSitePingMinutes());
        }
        location.setUpdatedAt(Instant.now());

        businessRepo.save(business);
        locationRepo.save(location);
        return toResponse(location, business);
    }

    private LocationSettings requireLocation() {
        UUID companyId = CompanyContext.get();
        if (companyId != null) {
            return locationRepo.findByCompanyId(companyId)
                    .orElseThrow(() -> new ApiException(ErrorCode.INTERNAL_ERROR,
                            "Location settings not initialised"));
        }
        return locationRepo.findById((short) 1)
                .orElseThrow(() -> new ApiException(ErrorCode.INTERNAL_ERROR,
                        "Location settings not initialised"));
    }

    private BusinessSettings requireBusiness() {
        UUID companyId = CompanyContext.get();
        if (companyId != null) {
            return businessRepo.findByCompanyId(companyId)
                    .orElseThrow(() -> new ApiException(ErrorCode.INTERNAL_ERROR,
                            "Business settings not initialised"));
        }
        return businessRepo.findById((short) 1)
                .orElseThrow(() -> new ApiException(ErrorCode.INTERNAL_ERROR,
                        "Business settings not initialised"));
    }

    private static LocationSettingsResponse toResponse(LocationSettings location, BusinessSettings business) {
        return new LocationSettingsResponse(
                business.getShopLat(),
                business.getShopLng(),
                business.getGeofenceRadiusM(),
                location.isCheckInEnabled(),
                location.isAttendanceEnabled(),
                location.isTrackingEnabled(),
                location.isTravelEnabled(),
                location.isEtaEnabled(),
                location.getPingIntervalMinutes(),
                location.getPingDistanceM(),
                location.getDayAutoEndTime(),
                location.isAllowStartOutsideGeofence(),
                location.getFarFromAddressM(),
                location.getRateTwoWheelerPaise(),
                location.getRateFourWheelerPaise(),
                location.getRetentionDays(),
                location.getPrivacyNoticeVersion(),
                location.isStrictTripMode(),
                location.isTripOpenMaps(),
                location.getTravelPingSeconds(),
                location.getOnSitePingMinutes());
    }
}
