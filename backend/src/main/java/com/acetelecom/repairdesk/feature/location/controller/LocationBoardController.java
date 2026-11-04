package com.acetelecom.repairdesk.feature.location.controller;

import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.acetelecom.repairdesk.feature.location.dto.LocationBoardResponse;
import com.acetelecom.repairdesk.feature.location.service.LocationBoardService;

@RestController
@RequestMapping("/location/board")
public class LocationBoardController {

    private final LocationBoardService service;

    public LocationBoardController(LocationBoardService service) {
        this.service = service;
    }

    /** Staff and Admin: office, technician positions, and travel time to open jobs. */
    @GetMapping
    @Secured({ "ROLE_STAFF", "ROLE_ADMIN" })
    public LocationBoardResponse board() {
        return service.board();
    }
}
