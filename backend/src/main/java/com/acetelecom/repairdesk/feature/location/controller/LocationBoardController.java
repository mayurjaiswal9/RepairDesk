package com.acetelecom.repairdesk.feature.location.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.acetelecom.repairdesk.feature.location.dto.LocationBoardResponse;
import com.acetelecom.repairdesk.feature.location.dto.LocationHistoryResponse;
import com.acetelecom.repairdesk.feature.location.service.LocationBoardService;
import com.acetelecom.repairdesk.feature.location.service.LocationHistoryService;

@RestController
@RequestMapping("/location/board")
public class LocationBoardController {

    private final LocationBoardService service;
    private final LocationHistoryService history;

    public LocationBoardController(LocationBoardService service, LocationHistoryService history) {
        this.service = service;
        this.history = history;
    }

    /** Admin: office, technician positions, and travel time to open jobs. */
    @GetMapping
    @Secured("ROLE_ADMIN")
    public LocationBoardResponse board() {
        return service.board();
    }

    /** Shared positions for one India day inside the two-day window. Newest first. */
    @GetMapping("/history")
    @Secured("ROLE_ADMIN")
    public LocationHistoryResponse history(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return history.history(date);
    }
}
