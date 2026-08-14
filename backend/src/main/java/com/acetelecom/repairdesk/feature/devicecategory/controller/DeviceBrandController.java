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

import com.acetelecom.repairdesk.feature.devicecategory.dto.DeviceBrandRequest;
import com.acetelecom.repairdesk.feature.devicecategory.dto.DeviceBrandResponse;
import com.acetelecom.repairdesk.feature.devicecategory.service.DeviceBrandService;

import jakarta.validation.Valid;

/**
 * Brands curated per device category (PROD-004). Listing active brands is
 * open to any authenticated role (the New Request screen needs it for
 * customers); mutation and the archived-inclusive listing are admin-only.
 */
@RestController
@RequestMapping("/device-categories/{categoryId}/brands")
public class DeviceBrandController {

    private final DeviceBrandService service;

    public DeviceBrandController(DeviceBrandService service) {
        this.service = service;
    }

    @GetMapping
    public List<DeviceBrandResponse> listActive(@PathVariable UUID categoryId) {
        return service.listActive(categoryId);
    }

    @GetMapping("/all")
    @Secured("ROLE_ADMIN")
    public List<DeviceBrandResponse> listAll(@PathVariable UUID categoryId) {
        return service.listAll(categoryId);
    }

    @PostMapping
    @Secured("ROLE_ADMIN")
    public ResponseEntity<DeviceBrandResponse> create(@PathVariable UUID categoryId,
            @Valid @RequestBody DeviceBrandRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(categoryId, req));
    }

    @PutMapping("/{brandId}")
    @Secured("ROLE_ADMIN")
    public DeviceBrandResponse update(@PathVariable UUID categoryId, @PathVariable UUID brandId,
            @Valid @RequestBody DeviceBrandRequest req) {
        return service.update(categoryId, brandId, req);
    }

    @PostMapping("/{brandId}/archive")
    @Secured("ROLE_ADMIN")
    public ResponseEntity<Void> archive(@PathVariable UUID categoryId, @PathVariable UUID brandId) {
        service.archive(categoryId, brandId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{brandId}/unarchive")
    @Secured("ROLE_ADMIN")
    public ResponseEntity<Void> unarchive(@PathVariable UUID categoryId, @PathVariable UUID brandId) {
        service.unarchive(categoryId, brandId);
        return ResponseEntity.noContent().build();
    }
}
