package com.acetelecom.repairdesk.feature.location.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.LocalTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.acetelecom.repairdesk.common.error.ApiException;
import com.acetelecom.repairdesk.feature.location.domain.LocationSettings;
import com.acetelecom.repairdesk.feature.location.dto.UpdateLocationSettingsRequest;
import com.acetelecom.repairdesk.feature.location.repository.LocationSettingsRepository;
import com.acetelecom.repairdesk.feature.settings.domain.BusinessSettings;
import com.acetelecom.repairdesk.feature.settings.repository.BusinessSettingsRepository;

@ExtendWith(MockitoExtension.class)
class LocationSettingsServiceTest {

    @Mock private LocationSettingsRepository locationRepo;
    @Mock private BusinessSettingsRepository businessRepo;

    private LocationSettingsService service;
    private LocationSettings location;
    private BusinessSettings business;

    @BeforeEach
    void setUp() {
        location = new LocationSettings();
        business = new BusinessSettings();
        service = new LocationSettingsService(locationRepo, businessRepo);
    }

    private void stubRows() {
        when(locationRepo.findById((short) 1)).thenReturn(Optional.of(location));
        when(businessRepo.findById((short) 1)).thenReturn(Optional.of(business));
    }

    @Test
    void config_doesNotExposeAllowanceRates() {
        stubRows();
        location.setRateTwoWheelerPaise(500);
        var config = service.config();
        assertThat(config.geofenceRadiusM()).isEqualTo(150);
        assertThat(config.checkInEnabled()).isFalse();
    }

    @Test
    void update_rejectsASingleShopCoordinate() {
        assertThatThrownBy(() -> service.update(request(19.1, null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("together");
    }

    @Test
    void update_savesShopPinAndSwitches() {
        stubRows();
        when(businessRepo.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> inv.getArgument(0));
        when(locationRepo.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> inv.getArgument(0));

        var saved = service.update(request(19.14, 72.82));

        assertThat(saved.shopLatitude()).isEqualTo(19.14);
        assertThat(saved.checkInEnabled()).isTrue();
        assertThat(business.getGeofenceRadiusM()).isEqualTo(200);
        assertThat(location.isCheckInEnabled()).isTrue();
    }

    private static UpdateLocationSettingsRequest request(Double lat, Double lng) {
        return new UpdateLocationSettingsRequest(
                lat, lng, 200,
                true, false, false, false, false,
                5, 200, LocalTime.of(19, 0), false,
                300, 500, 1000, 90, "1",
                null, null, null, null);
    }
}
