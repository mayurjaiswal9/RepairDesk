package com.acetelecom.repairdesk.feature.location.service;

import java.util.List;
import java.util.Optional;

/**
 * Outbound Google Maps calls. Implementations must not log the API key.
 */
public interface GoogleMapsClient {

    boolean isConfigured();

    Optional<GeocodedPlace> geocode(String normalizedAddress);

    Optional<RouteLeg> route(double fromLat, double fromLng, double toLat, double toLng, String travelMode);

    /** Empty when the call failed. An empty list means Google had no matches. */
    Optional<List<PlaceSuggestion>> suggest(String input, String sessionToken, Double biasLat, Double biasLng);

    Optional<GeocodedPlace> place(String placeId, String sessionToken);

    record PlaceSuggestion(String placeId, String primaryText, String secondaryText) {
    }

    record GeocodedPlace(double latitude, double longitude, String formattedAddress) {
    }

    record RouteLeg(int distanceMetres, int durationSeconds) {
    }
}
