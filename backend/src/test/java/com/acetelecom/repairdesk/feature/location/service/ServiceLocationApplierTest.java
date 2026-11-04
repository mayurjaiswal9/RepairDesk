package com.acetelecom.repairdesk.feature.location.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.acetelecom.repairdesk.common.error.ApiException;
import com.acetelecom.repairdesk.feature.location.domain.PinSource;
import com.acetelecom.repairdesk.feature.location.domain.ServiceLocation;
import com.acetelecom.repairdesk.feature.location.dto.ServiceLocationDto;
import com.acetelecom.repairdesk.feature.location.service.MapsService.GeocodeResult;
import com.acetelecom.repairdesk.feature.location.service.MapsService.Status;

@ExtendWith(MockitoExtension.class)
class ServiceLocationApplierTest {

    @Mock private MapsService mapsService;
    private ServiceLocationApplier applier;

    @BeforeEach
    void setUp() {
        applier = new ServiceLocationApplier(mapsService);
    }

    @Test
    void nullDto_leavesTheTargetUntouched() {
        ServiceLocation target = new ServiceLocation();
        target.setCity("Pune");
        applier.apply(target, null);
        assertThat(target.getCity()).isEqualTo("Pune");
    }

    @Test
    void coordinates_areKeptAsAManualPin() {
        ServiceLocation target = new ServiceLocation();
        applier.apply(target, new ServiceLocationDto(
                "Lokhandwala", "Near mall", "Mumbai", "400053", 19.14, 72.82, null));
        assertThat(target.getPinSource()).isEqualTo(PinSource.MANUAL_PIN);
        assertThat(target.getLatitude()).isEqualTo(19.14);
        assertThat(target.getPincode()).isEqualTo("400053");
    }

    @Test
    void addressWithoutPin_isGeocoded() {
        when(mapsService.geocode("Lokhandwala, Mumbai, 400053"))
                .thenReturn(new GeocodeResult(Status.FOUND, 19.14, 72.82, "Lokhandwala, Mumbai"));
        ServiceLocation target = new ServiceLocation();
        applier.apply(target, new ServiceLocationDto(
                "Lokhandwala", null, "Mumbai", "400053", null, null, null));
        assertThat(target.getPinSource()).isEqualTo(PinSource.GEOCODED);
        assertThat(target.getLatitude()).isEqualTo(19.14);
    }

    @Test
    void geocodeMiss_keepsTheTextAndNoPin() {
        when(mapsService.geocode(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(GeocodeResult.of(Status.UNAVAILABLE));
        ServiceLocation target = new ServiceLocation();
        applier.apply(target, new ServiceLocationDto(
                "Somewhere", null, "Mumbai", null, null, null, null));
        assertThat(target.getCity()).isEqualTo("Mumbai");
        assertThat(target.getLatitude()).isNull();
        assertThat(target.getPinSource()).isNull();
    }

    @Test
    void oneCoordinate_isRejected() {
        ServiceLocation target = new ServiceLocation();
        assertThatThrownBy(() -> applier.apply(target,
                new ServiceLocationDto(null, null, null, null, 19.14, null, null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("together");
    }
}
