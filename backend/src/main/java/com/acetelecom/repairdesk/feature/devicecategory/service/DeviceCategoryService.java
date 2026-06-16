package com.acetelecom.repairdesk.feature.devicecategory.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acetelecom.repairdesk.common.error.ApiException;
import com.acetelecom.repairdesk.common.error.ErrorCode;
import com.acetelecom.repairdesk.feature.devicecategory.domain.DeviceCategory;
import com.acetelecom.repairdesk.feature.devicecategory.dto.DeviceCategoryRequest;
import com.acetelecom.repairdesk.feature.devicecategory.dto.DeviceCategoryResponse;
import com.acetelecom.repairdesk.feature.devicecategory.repository.DeviceCategoryRepository;

@Service
@Transactional
public class DeviceCategoryService {

    private final DeviceCategoryRepository repo;

    public DeviceCategoryService(DeviceCategoryRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public List<DeviceCategoryResponse> listActive() {
        return repo.findAllByArchivedFalseOrderByNameAsc()
                .stream().map(DeviceCategoryResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<DeviceCategoryResponse> listAll() {
        return repo.findAllByOrderByArchivedAscNameAsc()
                .stream().map(DeviceCategoryResponse::from).toList();
    }

    public DeviceCategoryResponse create(DeviceCategoryRequest req) {
        checkNameUnique(req.name(), null);
        DeviceCategory cat = new DeviceCategory();
        cat.setName(req.name().strip());
        cat.setDescription(req.description());
        return DeviceCategoryResponse.from(repo.save(cat));
    }

    public DeviceCategoryResponse update(UUID id, DeviceCategoryRequest req) {
        DeviceCategory cat = require(id);
        if (!cat.getName().equalsIgnoreCase(req.name())) {
            checkNameUnique(req.name(), id);
        }
        cat.setName(req.name().strip());
        cat.setDescription(req.description());
        cat.setUpdatedAt(Instant.now());
        return DeviceCategoryResponse.from(repo.save(cat));
    }

    public void archive(UUID id) {
        DeviceCategory cat = require(id);
        cat.setArchived(true);
        cat.setUpdatedAt(Instant.now());
        repo.save(cat);
    }

    public void unarchive(UUID id) {
        DeviceCategory cat = require(id);
        checkNameUnique(cat.getName(), id);
        cat.setArchived(false);
        cat.setUpdatedAt(Instant.now());
        repo.save(cat);
    }

    private DeviceCategory require(UUID id) {
        return repo.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Device category not found"));
    }

    private void checkNameUnique(String name, UUID excludeId) {
        repo.findByNameIgnoreCaseAndArchivedFalse(name).ifPresent(existing -> {
            if (!existing.getId().equals(excludeId)) {
                throw new ApiException(ErrorCode.CONFLICT,
                        "An active device category with this name already exists");
            }
        });
    }
}
