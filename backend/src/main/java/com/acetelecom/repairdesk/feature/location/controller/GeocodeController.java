package com.acetelecom.repairdesk.feature.location.controller;

import java.time.Clock;

import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.acetelecom.repairdesk.common.error.ApiException;
import com.acetelecom.repairdesk.common.error.ErrorCode;
import com.acetelecom.repairdesk.feature.location.dto.GeocodeRequest;
import com.acetelecom.repairdesk.feature.location.dto.GeocodeResponse;
import com.acetelecom.repairdesk.feature.location.service.GeocodeRateLimiter;
import com.acetelecom.repairdesk.feature.location.service.MapsService;
import com.acetelecom.repairdesk.feature.location.service.MapsService.GeocodeResult;
import com.acetelecom.repairdesk.security.CurrentUser;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/geocode")
public class GeocodeController {

    private final MapsService mapsService;
    private final GeocodeRateLimiter rateLimiter;
    private final CurrentUser currentUser;
    private final Clock clock;

    public GeocodeController(MapsService mapsService, GeocodeRateLimiter rateLimiter,
            CurrentUser currentUser, Clock clock) {
        this.mapsService = mapsService;
        this.rateLimiter = rateLimiter;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    @PostMapping
    @Secured({ "ROLE_CUSTOMER", "ROLE_STAFF", "ROLE_ADMIN" })
    public GeocodeResponse geocode(@Valid @RequestBody GeocodeRequest body) {
        if (!rateLimiter.allow(currentUser.get().id(), clock.instant())) {
            throw new ApiException(ErrorCode.TOO_MANY_REQUESTS,
                    "Address lookup limit reached. Try again in an hour, or place the pin on the map.");
        }
        GeocodeResult result = mapsService.geocode(body.address());
        return switch (result.status()) {
            case FOUND -> new GeocodeResponse(result.latitude(), result.longitude(), result.formattedAddress());
            case LIMIT_REACHED -> throw new ApiException(ErrorCode.TOO_MANY_REQUESTS,
                    "Map lookups are paused for today. Place the pin on the map.");
            case NOT_FOUND -> throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "That address was not found. Place the pin on the map.");
            case UNAVAILABLE -> throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Map lookup is unavailable. Place the pin on the map.");
        };
    }
}
