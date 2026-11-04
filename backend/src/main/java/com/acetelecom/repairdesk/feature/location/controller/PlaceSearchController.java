package com.acetelecom.repairdesk.feature.location.controller;

import java.time.Clock;

import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.acetelecom.repairdesk.common.error.ApiException;
import com.acetelecom.repairdesk.common.error.ErrorCode;
import com.acetelecom.repairdesk.feature.location.dto.CustomerPinRequest;
import com.acetelecom.repairdesk.feature.location.dto.GeocodeResponse;
import com.acetelecom.repairdesk.feature.location.dto.PlaceSelectRequest;
import com.acetelecom.repairdesk.feature.location.dto.PlaceSuggestRequest;
import com.acetelecom.repairdesk.feature.location.dto.PlaceSuggestionsResponse;
import com.acetelecom.repairdesk.feature.location.dto.PlaceSuggestionsResponse.PlaceSuggestionResponse;
import com.acetelecom.repairdesk.feature.location.service.CustomerPinParser.Pin;
import com.acetelecom.repairdesk.feature.location.service.CustomerPinService;
import com.acetelecom.repairdesk.feature.location.service.MapsService;
import com.acetelecom.repairdesk.feature.location.service.MapsService.GeocodeResult;
import com.acetelecom.repairdesk.feature.location.service.MapsService.SuggestOutcome;
import com.acetelecom.repairdesk.feature.location.service.SuggestionRateLimiter;
import com.acetelecom.repairdesk.security.CurrentUser;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/location")
public class PlaceSearchController {

    private final MapsService mapsService;
    private final CustomerPinService customerPins;
    private final SuggestionRateLimiter rateLimiter;
    private final CurrentUser currentUser;
    private final Clock clock;

    public PlaceSearchController(MapsService mapsService, CustomerPinService customerPins,
            SuggestionRateLimiter rateLimiter, CurrentUser currentUser, Clock clock) {
        this.mapsService = mapsService;
        this.customerPins = customerPins;
        this.rateLimiter = rateLimiter;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    @PostMapping("/suggestions")
    @Secured({ "ROLE_CUSTOMER", "ROLE_STAFF", "ROLE_ADMIN" })
    public PlaceSuggestionsResponse suggest(@Valid @RequestBody PlaceSuggestRequest body) {
        if (!rateLimiter.allow(currentUser.get().id(), clock.instant())) {
            throw new ApiException(ErrorCode.TOO_MANY_REQUESTS,
                    "Place search is paused for an hour. Drop the pin on the map.");
        }
        SuggestOutcome outcome = mapsService.suggest(
                body.input().trim(), body.sessionToken(), body.latitude(), body.longitude());
        return switch (outcome.status()) {
            case FOUND -> new PlaceSuggestionsResponse(outcome.suggestions().stream()
                    .map(item -> new PlaceSuggestionResponse(item.placeId(), item.primaryText(), item.secondaryText()))
                    .toList());
            case LIMIT_REACHED -> throw new ApiException(ErrorCode.TOO_MANY_REQUESTS,
                    "Place search is paused for today. Drop the pin on the map.");
            case NOT_FOUND, UNAVAILABLE -> throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Place search is unavailable. Drop the pin on the map.");
        };
    }

    @PostMapping("/suggestions/select")
    @Secured({ "ROLE_CUSTOMER", "ROLE_STAFF", "ROLE_ADMIN" })
    public GeocodeResponse select(@Valid @RequestBody PlaceSelectRequest body) {
        if (!rateLimiter.allow(currentUser.get().id(), clock.instant())) {
            throw new ApiException(ErrorCode.TOO_MANY_REQUESTS,
                    "Place search is paused for an hour. Drop the pin on the map.");
        }
        GeocodeResult result = mapsService.place(body.placeId(), body.sessionToken());
        return switch (result.status()) {
            case FOUND -> new GeocodeResponse(result.latitude(), result.longitude(), result.formattedAddress());
            case LIMIT_REACHED -> throw new ApiException(ErrorCode.TOO_MANY_REQUESTS,
                    "Place search is paused for today. Drop the pin on the map.");
            case NOT_FOUND -> throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "That place could not be opened. Drop the pin on the map.");
            case UNAVAILABLE -> throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Place search is unavailable. Drop the pin on the map.");
        };
    }

    @PostMapping("/customer-pin")
    @Secured({ "ROLE_CUSTOMER", "ROLE_STAFF", "ROLE_ADMIN" })
    public GeocodeResponse customerPin(@Valid @RequestBody CustomerPinRequest body) {
        Pin pin = customerPins.resolve(body.text(), currentUser.get().id(), clock.instant())
                .orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_ERROR,
                        "That message has no map pin. Paste the Google Maps link the customer sent, or drop the pin on the map."));
        return new GeocodeResponse(pin.latitude(), pin.longitude(), null);
    }
}
