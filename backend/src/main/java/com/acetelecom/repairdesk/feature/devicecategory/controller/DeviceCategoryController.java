package com.acetelecom.repairdesk.feature.devicecategory.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.acetelecom.repairdesk.feature.devicecategory.dto.DeviceCategoryRequest;
import com.acetelecom.repairdesk.feature.devicecategory.dto.DeviceCategoryResponse;
import com.acetelecom.repairdesk.feature.devicecategory.service.DeviceCategoryService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/device-categories")
public class DeviceCategoryController {

    private final DeviceCategoryService service;

    public DeviceCategoryController(DeviceCategoryService service) {
        this.service = service;
    }

    @GetMapping
    public List<DeviceCategoryResponse> listActive() {
        return service.listActive();
    }

    @GetMapping("/all")
    @Secured("ROLE_ADMIN")
    public List<DeviceCategoryResponse> listAll() {
        return service.listAll();
    }

    @PostMapping
    @Secured("ROLE_ADMIN")
    public ResponseEntity<DeviceCategoryResponse> create(@Valid @RequestBody DeviceCategoryRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(req));
    }

    @PutMapping("/{id}")
    @Secured("ROLE_ADMIN")
    public DeviceCategoryResponse update(@PathVariable UUID id,
            @Valid @RequestBody DeviceCategoryRequest req) {
        return service.update(id, req);
    }

    @PostMapping("/{id}/archive")
    @Secured("ROLE_ADMIN")
    public ResponseEntity<Void> archive(@PathVariable UUID id) {
        service.archive(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/unarchive")
    @Secured("ROLE_ADMIN")
    public ResponseEntity<Void> unarchive(@PathVariable UUID id) {
        service.unarchive(id);
        return ResponseEntity.noContent().build();
    }
}
