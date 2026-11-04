package com.acetelecom.repairdesk.feature.location.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acetelecom.repairdesk.config.AppProperties;
import com.acetelecom.repairdesk.feature.company.CompanyContext;
import com.acetelecom.repairdesk.feature.location.domain.GeocodeCacheEntry;
import com.acetelecom.repairdesk.feature.location.domain.MapsApiUsage;
import com.acetelecom.repairdesk.feature.location.repository.GeocodeCacheRepository;
import com.acetelecom.repairdesk.feature.location.repository.MapsApiUsageRepository;
import com.acetelecom.repairdesk.feature.location.service.GoogleMapsClient.GeocodedPlace;
import com.acetelecom.repairdesk.feature.location.service.GoogleMapsClient.PlaceSuggestion;
import com.acetelecom.repairdesk.feature.location.service.GoogleMapsClient.RouteLeg;

/**
 * Geocoding and routing with a cache, a daily counter, and a straight-line
 * fallback once the counter reaches the configured limit.
 */
@Service
public class MapsService {

    public enum Status {
        FOUND, NOT_FOUND, LIMIT_REACHED, UNAVAILABLE
    }

    public record GeocodeResult(
            Status status, Double latitude, Double longitude, String formattedAddress) {

        public static GeocodeResult of(Status status) {
            return new GeocodeResult(status, null, null, null);
        }
    }

    /** {@code estimated} is true when the figure did not come from the Routes API. */
    public record RouteEstimate(int distanceMetres, Integer durationSeconds, boolean estimated) {
    }

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    /** Used only for the straight-line fallback duration. */
    private static final double FALLBACK_SPEED_MPS = 20_000d / 3_600d;

    private final GoogleMapsClient client;
    private final GeocodeCacheRepository cacheRepo;
    private final MapsApiUsageRepository usageRepo;
    private final AppProperties appProperties;
    private final Clock clock;

    public MapsService(GoogleMapsClient client, GeocodeCacheRepository cacheRepo,
            MapsApiUsageRepository usageRepo, AppProperties appProperties, Clock clock) {
        this.client = client;
        this.cacheRepo = cacheRepo;
        this.usageRepo = usageRepo;
        this.appProperties = appProperties;
        this.clock = clock;
    }

    @Transactional
    public GeocodeResult geocode(String address) {
        if (address == null || address.isBlank()) {
            return GeocodeResult.of(Status.NOT_FOUND);
        }
        String normalized = AddressNormalizer.normalize(address);
        String hash = AddressNormalizer.sha256(normalized);
        Optional<GeocodeCacheEntry> cached = cacheRepo.findById(hash);
        if (cached.isPresent()) {
            GeocodeCacheEntry hit = cached.get();
            return new GeocodeResult(Status.FOUND, hit.getLat(), hit.getLng(), hit.getFormattedAddress());
        }
        if (!client.isConfigured()) {
            return GeocodeResult.of(Status.UNAVAILABLE);
        }
        if (!reserveCall(MapsApiUsage.GEOCODE, appProperties.getMaps().getDailyLimitGeocode())) {
            return GeocodeResult.of(Status.LIMIT_REACHED);
        }
        Optional<GeocodedPlace> place = client.geocode(normalized);
        if (place.isEmpty()) {
            return GeocodeResult.of(Status.NOT_FOUND);
        }
        GeocodedPlace found = place.get();
        GeocodeCacheEntry entry = new GeocodeCacheEntry();
        entry.setAddressHash(hash);
        entry.setNormalizedAddress(truncate(normalized, 500));
        entry.setLat(found.latitude());
        entry.setLng(found.longitude());
        entry.setFormattedAddress(truncate(found.formattedAddress(), 500));
        cacheRepo.save(entry);
        return new GeocodeResult(Status.FOUND, found.latitude(), found.longitude(), found.formattedAddress());
    }

    @Transactional
    public RouteEstimate route(double fromLat, double fromLng, double toLat, double toLng, String travelMode) {
        String mode = "DRIVE".equals(travelMode) ? "DRIVE" : "TWO_WHEELER";
        double straight = GeoMath.metresBetween(fromLat, fromLng, toLat, toLng);
        int fallbackMetres = (int) Math.round(straight * 1.3d);
        if (!client.isConfigured()
                || !reserveCall(MapsApiUsage.ROUTES, appProperties.getMaps().getDailyLimitRoutes())) {
            return new RouteEstimate(fallbackMetres, fallbackSeconds(fallbackMetres), true);
        }
        Optional<RouteLeg> leg = client.route(fromLat, fromLng, toLat, toLng, mode);
        if (leg.isEmpty()) {
            return new RouteEstimate(fallbackMetres, fallbackSeconds(fallbackMetres), true);
        }
        return new RouteEstimate(leg.get().distanceMetres(), leg.get().durationSeconds(), false);
    }

    @Transactional
    public SuggestOutcome suggest(String input, String sessionToken, Double biasLat, Double biasLng) {
        if (!client.isConfigured()) {
            return new SuggestOutcome(Status.UNAVAILABLE, List.of());
        }
        if (!reserveCall(MapsApiUsage.SUGGESTIONS, appProperties.getMaps().getDailyLimitSuggestions())) {
            return new SuggestOutcome(Status.LIMIT_REACHED, List.of());
        }
        Optional<List<PlaceSuggestion>> suggestions = client.suggest(input, sessionToken, biasLat, biasLng);
        if (suggestions.isEmpty()) {
            return new SuggestOutcome(Status.UNAVAILABLE, List.of());
        }
        return new SuggestOutcome(Status.FOUND, suggestions.get());
    }

    @Transactional
    public GeocodeResult place(String placeId, String sessionToken) {
        if (!client.isConfigured()) {
            return GeocodeResult.of(Status.UNAVAILABLE);
        }
        if (!reserveCall(MapsApiUsage.PLACE_DETAILS, appProperties.getMaps().getDailyLimitSuggestions())) {
            return GeocodeResult.of(Status.LIMIT_REACHED);
        }
        Optional<GeocodedPlace> found = client.place(placeId, sessionToken);
        if (found.isEmpty()) {
            return GeocodeResult.of(Status.NOT_FOUND);
        }
        GeocodedPlace place = found.get();
        return new GeocodeResult(Status.FOUND, place.latitude(), place.longitude(), place.formattedAddress());
    }

    public record SuggestOutcome(Status status, List<PlaceSuggestion> suggestions) {
    }

    private boolean reserveCall(String api, int limit) {
        LocalDate today = LocalDate.now(clock.withZone(IST));
        MapsApiUsage.Key key = new MapsApiUsage.Key(today, api);
        MapsApiUsage row = usageRepo.findById(key).orElse(null);
        if (row == null) {
            row = new MapsApiUsage(today, api);
            row.setCompanyId(CompanyContext.get());
        }
        if (row.getCallCount() >= limit) {
            return false;
        }
        row.setCallCount(row.getCallCount() + 1);
        usageRepo.save(row);
        return true;
    }

    private static int fallbackSeconds(int metres) {
        return (int) Math.round(metres / FALLBACK_SPEED_MPS);
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }
}
