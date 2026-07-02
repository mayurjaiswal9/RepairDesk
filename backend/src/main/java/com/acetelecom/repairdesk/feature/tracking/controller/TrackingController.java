package com.acetelecom.repairdesk.feature.tracking.controller;

import java.util.List;

import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.acetelecom.repairdesk.feature.tracking.dto.RepairDetail;
import com.acetelecom.repairdesk.feature.tracking.dto.RepairListItem;
import com.acetelecom.repairdesk.feature.tracking.service.TrackingService;
import com.acetelecom.repairdesk.security.CurrentUser;

@RestController
@RequestMapping("/me/repairs")
@Secured("ROLE_CUSTOMER")
public class TrackingController {

    private final TrackingService service;
    private final CurrentUser currentUser;

    public TrackingController(TrackingService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<RepairListItem> list() {
        return service.list(currentUser.get().id());
    }

    @GetMapping("/{referenceNumber}")
    public RepairDetail detail(@PathVariable String referenceNumber) {
        return service.detail(referenceNumber, currentUser.get().id());
    }
}
