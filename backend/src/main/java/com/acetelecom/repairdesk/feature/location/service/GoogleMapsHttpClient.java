package com.acetelecom.repairdesk.feature.location.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import com.acetelecom.repairdesk.config.AppProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@Component
public class GoogleMapsHttpClient implements GoogleMapsClient {

    private static final Logger log = LoggerFactory.getLogger(GoogleMapsHttpClient.class);

    private final RestClient http;
    private final AppProperties appProperties;

    public GoogleMapsHttpClient(AppProperties appProperties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.http = RestClient.builder().requestFactory(factory).build();
        this.appProperties = appProperties;
    }

    @Override
    public boolean isConfigured() {
        String key = appProperties.getMaps().getServerKey();
        return key != null && !key.isBlank();
    }

    @Override
    public Optional<GeocodedPlace> geocode(String normalizedAddress) {
        if (!isConfigured()) {
            return Optional.empty();
        }
        var uri = UriComponentsBuilder
                .fromUriString("https://maps.googleapis.com/maps/api/geocode/json")
                .queryParam("address", normalizedAddress)
                .queryParam("region", "in")
                .queryParam("key", appProperties.getMaps().getServerKey())
                .build()
                .encode()
                .toUri();
        try {
            GeocodeBody body = http.get().uri(uri).retrieve().body(GeocodeBody.class);
            if (body == null || !"OK".equals(body.status()) || body.results() == null || body.results().isEmpty()) {
                log.info("Geocoding status {}", body == null ? "empty" : body.status());
                return Optional.empty();
            }
            GeocodeBody.Result first = body.results().get(0);
            if (first.geometry() == null || first.geometry().location() == null) {
                return Optional.empty();
            }
            GeocodeBody.LatLng loc = first.geometry().location();
            return Optional.of(new GeocodedPlace(loc.lat(), loc.lng(), first.formattedAddress()));
        } catch (RestClientException ex) {
            log.warn("Geocoding call failed: {}", ex.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    @Override
    public Optional<RouteLeg> route(double fromLat, double fromLng, double toLat, double toLng, String travelMode) {
        if (!isConfigured()) {
            return Optional.empty();
        }
        Map<String, Object> body = Map.of(
                "origin", point(fromLat, fromLng),
                "destination", point(toLat, toLng),
                "travelMode", travelMode);
        try {
            RoutesBody response = http.post()
                    .uri("https://routes.googleapis.com/directions/v2:computeRoutes")
                    .header("X-Goog-Api-Key", appProperties.getMaps().getServerKey())
                    .header("X-Goog-FieldMask", "routes.distanceMeters,routes.duration")
                    .body(body)
                    .retrieve()
                    .body(RoutesBody.class);
            if (response == null || response.routes() == null || response.routes().isEmpty()) {
                return Optional.empty();
            }
            RoutesBody.Route route = response.routes().get(0);
            Integer seconds = parseSeconds(route.duration());
            if (route.distanceMeters() == null || seconds == null) {
                return Optional.empty();
            }
            return Optional.of(new RouteLeg(route.distanceMeters(), seconds));
        } catch (RestClientException ex) {
            log.warn("Routes call failed: {}", ex.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    @Override
    public Optional<List<PlaceSuggestion>> suggest(String input, String sessionToken, Double biasLat, Double biasLng) {
        if (!isConfigured()) {
            return Optional.empty();
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("input", input);
        body.put("sessionToken", sessionToken);
        body.put("includedRegionCodes", List.of("in"));
        if (biasLat != null && biasLng != null) {
            body.put("locationBias", Map.of(
                    "circle", Map.of(
                            "center", Map.of("latitude", biasLat, "longitude", biasLng),
                            "radius", 20_000.0)));
        }
        try {
            SuggestBody response = http.post()
                    .uri("https://places.googleapis.com/v1/places:autocomplete")
                    .header("X-Goog-Api-Key", appProperties.getMaps().getServerKey())
                    .header("X-Goog-FieldMask",
                            "suggestions.placePrediction.placeId,suggestions.placePrediction.text,suggestions.placePrediction.structuredFormat")
                    .body(body)
                    .retrieve()
                    .body(SuggestBody.class);
            if (response == null || response.suggestions() == null) {
                return Optional.of(List.of());
            }
            List<PlaceSuggestion> suggestions = new ArrayList<>();
            for (SuggestBody.Suggestion suggestion : response.suggestions()) {
                if (suggestion.placePrediction() == null || suggestion.placePrediction().placeId() == null) {
                    continue;
                }
                SuggestBody.Prediction prediction = suggestion.placePrediction();
                String primary = prediction.structuredFormat() != null && prediction.structuredFormat().mainText() != null
                        ? prediction.structuredFormat().mainText().text()
                        : prediction.text() == null ? "" : prediction.text().text();
                String secondary = prediction.structuredFormat() != null
                        && prediction.structuredFormat().secondaryText() != null
                                ? prediction.structuredFormat().secondaryText().text()
                                : "";
                if (primary != null && !primary.isBlank()) {
                    suggestions.add(new PlaceSuggestion(prediction.placeId(), primary, secondary == null ? "" : secondary));
                }
                if (suggestions.size() == 5) {
                    break;
                }
            }
            return Optional.of(suggestions);
        } catch (RestClientResponseException ex) {
            log.warn("Place suggestion HTTP {} {}", ex.getStatusCode().value(), googleMessage(ex));
            return Optional.empty();
        } catch (RestClientException ex) {
            log.warn("Place suggestion call failed: {}", ex.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    @Override
    public Optional<GeocodedPlace> place(String placeId, String sessionToken) {
        if (!isConfigured()) {
            return Optional.empty();
        }
        String uri = "https://places.googleapis.com/v1/places/" + placeId
                + "?sessionToken=" + URLEncoder.encode(sessionToken, StandardCharsets.UTF_8);
        try {
            PlaceBody response = http.get()
                    .uri(uri)
                    .header("X-Goog-Api-Key", appProperties.getMaps().getServerKey())
                    .header("X-Goog-FieldMask", "location,formattedAddress")
                    .retrieve()
                    .body(PlaceBody.class);
            if (response == null || response.location() == null) {
                return Optional.empty();
            }
            return Optional.of(new GeocodedPlace(
                    response.location().latitude(),
                    response.location().longitude(),
                    response.formattedAddress()));
        } catch (RestClientResponseException ex) {
            log.warn("Place details HTTP {} {}", ex.getStatusCode().value(), googleMessage(ex));
            return Optional.empty();
        } catch (RestClientException ex) {
            log.warn("Place details call failed: {}", ex.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /** Google's error text, with any key stripped. */
    private static String googleMessage(RestClientResponseException ex) {
        String text = ex.getResponseBodyAsString();
        if (text == null || text.isBlank()) {
            return ex.getStatusText();
        }
        text = text.replaceAll("AIza[0-9A-Za-z_\\-]+", "[key]");
        return text.length() > 240 ? text.substring(0, 240) : text;
    }

    private static Map<String, Object> point(double lat, double lng) {
        return Map.of("location", Map.of("latLng", Map.of("latitude", lat, "longitude", lng)));
    }

    /** Routes returns durations as strings such as {@code 668s}. */
    static Integer parseSeconds(String duration) {
        if (duration == null || !duration.endsWith("s")) {
            return null;
        }
        try {
            return (int) Math.round(Double.parseDouble(duration.substring(0, duration.length() - 1)));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GeocodeBody(String status, List<Result> results) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        record Result(@JsonProperty("formatted_address") String formattedAddress, Geometry geometry) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Geometry(LatLng location) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        record LatLng(double lat, double lng) {
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RoutesBody(List<Route> routes) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        record Route(Integer distanceMeters, String duration) {
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SuggestBody(List<Suggestion> suggestions) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        record Suggestion(Prediction placePrediction) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Prediction(String placeId, Text text, Structured structuredFormat) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Structured(Text mainText, Text secondaryText) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Text(String text) {
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PlaceBody(LatLng location, String formattedAddress) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        record LatLng(double latitude, double longitude) {
        }
    }
}
