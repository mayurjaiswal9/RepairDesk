package com.acetelecom.repairdesk.feature.trip.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.acetelecom.repairdesk.feature.trip.dto.AdjustDistanceRequest;
import com.acetelecom.repairdesk.feature.trip.dto.EndTripRequest;
import com.acetelecom.repairdesk.feature.trip.dto.JobTripsResponse;
import com.acetelecom.repairdesk.feature.trip.dto.StartTripRequest;
import com.acetelecom.repairdesk.feature.trip.dto.TripPointsRequest;
import com.acetelecom.repairdesk.feature.trip.dto.TripPointsResponse;
import com.acetelecom.repairdesk.feature.trip.dto.TripResponse;
import com.acetelecom.repairdesk.feature.trip.service.TripService;
import com.acetelecom.repairdesk.feature.trip.service.TripService.StartResult;
import com.acetelecom.repairdesk.security.CurrentUser;

import jakarta.validation.Valid;

@RestController
public class TripController {

    private final TripService service;
    private final CurrentUser currentUser;

    public TripController(TripService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    /** 201 for a new trip; 200 when this job's trip was already running. */
    @PostMapping("/jobs/{jobId}/trips")
    @Secured("ROLE_TECHNICIAN")
    public ResponseEntity<TripResponse> start(@PathVariable UUID jobId,
            @Valid @RequestBody(required = false) StartTripRequest body) {
        StartResult result = service.start(jobId, body, currentUser.get());
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.trip());
    }

    @GetMapping("/jobs/{jobId}/trips")
    @Secured({ "ROLE_STAFF", "ROLE_ADMIN", "ROLE_TECHNICIAN" })
    public JobTripsResponse jobTrips(@PathVariable UUID jobId) {
        return service.jobTrips(jobId, currentUser.get());
    }

    @PostMapping("/trips/{tripId}/points")
    @Secured("ROLE_TECHNICIAN")
    public TripPointsResponse points(@PathVariable UUID tripId, @Valid @RequestBody TripPointsRequest body) {
        return service.addPoints(tripId, body.points(), currentUser.get());
    }

    @PostMapping("/trips/{tripId}/end")
    @Secured("ROLE_TECHNICIAN")
    public TripResponse end(@PathVariable UUID tripId, @Valid @RequestBody EndTripRequest body) {
        return service.end(tripId, body, currentUser.get());
    }

    @PutMapping("/trips/{tripId}/distance")
    @Secured({ "ROLE_STAFF", "ROLE_ADMIN" })
    public TripResponse adjustDistance(@PathVariable UUID tripId, @Valid @RequestBody AdjustDistanceRequest body) {
        return service.adjustDistance(tripId, body, currentUser.get());
    }

    /** 204 when nothing is running. */
    @GetMapping("/me/trips/active")
    @Secured("ROLE_TECHNICIAN")
    public ResponseEntity<TripResponse> active() {
        return service.active(currentUser.get())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
