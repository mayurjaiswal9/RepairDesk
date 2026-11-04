package com.acetelecom.repairdesk.feature.location.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.acetelecom.repairdesk.config.AppProperties;
import com.acetelecom.repairdesk.feature.location.domain.GeocodeCacheEntry;
import com.acetelecom.repairdesk.feature.location.domain.MapsApiUsage;
import com.acetelecom.repairdesk.feature.location.repository.GeocodeCacheRepository;
import com.acetelecom.repairdesk.feature.location.repository.MapsApiUsageRepository;
import com.acetelecom.repairdesk.feature.location.service.GoogleMapsClient.GeocodedPlace;
import com.acetelecom.repairdesk.feature.location.service.MapsService.Status;

@ExtendWith(MockitoExtension.class)
class MapsServiceTest {

    @Mock private GoogleMapsClient client;
    @Mock private GeocodeCacheRepository cacheRepo;
    @Mock private MapsApiUsageRepository usageRepo;

    private MapsService service;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties();
        props.getMaps().setDailyLimitGeocode(2);
        props.getMaps().setDailyLimitRoutes(1);
        service = new MapsService(client, cacheRepo, usageRepo, props,
                Clock.fixed(Instant.parse("2026-10-17T10:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void normalizeAppendsIndiaOnce() {
        assertThat(AddressNormalizer.normalize("  Lokhandwala   Andheri "))
                .isEqualTo("lokhandwala andheri, india");
        assertThat(AddressNormalizer.normalize("Mumbai, India")).isEqualTo("mumbai, india");
    }

    @Test
    void geocode_cacheHit_doesNotCallGoogle() {
        String hash = AddressNormalizer.sha256(AddressNormalizer.normalize("Andheri Mumbai"));
        GeocodeCacheEntry entry = new GeocodeCacheEntry();
        entry.setLat(19.14);
        entry.setLng(72.82);
        entry.setFormattedAddress("Andheri, Mumbai");
        when(cacheRepo.findById(hash)).thenReturn(Optional.of(entry));

        var result = service.geocode("Andheri Mumbai");

        assertThat(result.status()).isEqualTo(Status.FOUND);
        assertThat(result.latitude()).isEqualTo(19.14);
        verify(client, never()).geocode(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void geocode_whenDailyLimitReached_doesNotCallGoogle() {
        when(cacheRepo.findById(org.mockito.ArgumentMatchers.anyString())).thenReturn(Optional.empty());
        when(client.isConfigured()).thenReturn(true);
        MapsApiUsage full = new MapsApiUsage(java.time.LocalDate.of(2026, 10, 17), MapsApiUsage.GEOCODE);
        full.setCallCount(2);
        when(usageRepo.findById(org.mockito.ArgumentMatchers.any())).thenReturn(Optional.of(full));

        var result = service.geocode("Andheri Mumbai");

        assertThat(result.status()).isEqualTo(Status.LIMIT_REACHED);
        verify(client, never()).geocode(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void geocode_success_storesCacheAndCountsTheCall() {
        when(cacheRepo.findById(org.mockito.ArgumentMatchers.anyString())).thenReturn(Optional.empty());
        when(client.isConfigured()).thenReturn(true);
        when(usageRepo.findById(org.mockito.ArgumentMatchers.any())).thenReturn(Optional.empty());
        when(usageRepo.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> inv.getArgument(0));
        when(client.geocode(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(Optional.of(new GeocodedPlace(19.14, 72.82, "Andheri, Mumbai")));
        when(cacheRepo.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> inv.getArgument(0));

        var result = service.geocode("Andheri Mumbai");

        assertThat(result.status()).isEqualTo(Status.FOUND);
        assertThat(result.formattedAddress()).isEqualTo("Andheri, Mumbai");
        verify(cacheRepo).save(org.mockito.ArgumentMatchers.any());
        verify(usageRepo).save(org.mockito.ArgumentMatchers.argThat(row ->
                ((MapsApiUsage) row).getCallCount() == 1));
    }

    @Test
    void route_whenNotConfigured_usesStraightLineFallback() {
        when(client.isConfigured()).thenReturn(false);

        var estimate = service.route(19.1197, 72.8468, 19.1415, 72.8250, "TWO_WHEELER");

        assertThat(estimate.estimated()).isTrue();
        assertThat(estimate.distanceMetres()).isGreaterThan(2_000);
        verify(client, never()).route(org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyDouble(), org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyDouble(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void parseSeconds_readsRoutesDuration() {
        assertThat(GoogleMapsHttpClient.parseSeconds("668s")).isEqualTo(668);
        assertThat(GoogleMapsHttpClient.parseSeconds("12.6s")).isEqualTo(13);
        assertThat(GoogleMapsHttpClient.parseSeconds(null)).isNull();
    }
}
