package com.acetelecom.repairdesk.feature.devicecategory.service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acetelecom.repairdesk.common.error.ApiException;
import com.acetelecom.repairdesk.common.error.ErrorCode;
import com.acetelecom.repairdesk.feature.company.CompanyContext;
import com.acetelecom.repairdesk.feature.devicecategory.domain.BrandMode;
import com.acetelecom.repairdesk.feature.devicecategory.domain.DeviceCategory;
import com.acetelecom.repairdesk.feature.devicecategory.dto.DeviceCategoryRequest;
import com.acetelecom.repairdesk.feature.devicecategory.dto.DeviceCategoryResponse;
import com.acetelecom.repairdesk.feature.devicecategory.repository.DeviceBrandRepository;
import com.acetelecom.repairdesk.feature.devicecategory.repository.DeviceCategoryRepository;

@Service
@Transactional
public class DeviceCategoryService {

    private final DeviceCategoryRepository repo;
    private final DeviceBrandRepository brandRepo;

    public DeviceCategoryService(DeviceCategoryRepository repo, DeviceBrandRepository brandRepo) {
        this.repo = repo;
        this.brandRepo = brandRepo;
    }

    @Transactional(readOnly = true)
    public List<DeviceCategoryResponse> listActive() {
        Map<UUID, Long> counts = activeBrandCounts();
        return repo.findAllByArchivedFalseOrderByNameAsc().stream()
                .map(c -> DeviceCategoryResponse.from(c, counts.getOrDefault(c.getId(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DeviceCategoryResponse> listAll() {
        Map<UUID, Long> counts = activeBrandCounts();
        return repo.findAllByOrderByArchivedAscNameAsc().stream()
                .map(c -> DeviceCategoryResponse.from(c, counts.getOrDefault(c.getId(), 0L)))
                .toList();
    }

    public DeviceCategoryResponse create(DeviceCategoryRequest req) {
        checkNameUnique(req.name(), null);
        DeviceCategory cat = new DeviceCategory();
        cat.setName(req.name().strip());
        cat.setDescription(req.description());
        cat.setBrandMode(req.brandMode() == null || req.brandMode().isBlank()
                ? BrandMode.FREE_TEXT
                : parseBrandMode(req.brandMode()));
        DeviceCategory saved = repo.save(cat);
        return DeviceCategoryResponse.from(saved, 0);
    }

    public DeviceCategoryResponse update(UUID id, DeviceCategoryRequest req) {
        DeviceCategory cat = require(id);
        if (!cat.getName().equalsIgnoreCase(req.name())) {
            checkNameUnique(req.name(), id);
        }
        cat.setName(req.name().strip());
        cat.setDescription(req.description());
        cat.setBrandMode(req.brandMode() == null || req.brandMode().isBlank()
                ? cat.getBrandMode()
                : parseBrandMode(req.brandMode()));
        cat.setUpdatedAt(Instant.now());
        DeviceCategory saved = repo.save(cat);
        long count = brandRepo.countByDeviceCategoryIdAndArchivedFalse(id);
        return DeviceCategoryResponse.from(saved, count);
    }

    private BrandMode parseBrandMode(String raw) {
        try {
            return BrandMode.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Unknown brand mode: " + raw);
        }
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

    private Map<UUID, Long> activeBrandCounts() {
        Map<UUID, Long> counts = new HashMap<>();
        for (Object[] row : brandRepo.countActiveGroupedByCategory()) {
            counts.put((UUID) row[0], (Long) row[1]);
        }
        return counts;
    }

    private DeviceCategory require(UUID id) {
        DeviceCategory category = repo.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Device category not found"));
        CompanyContext.requireSame(category.getCompanyId(), "Device category not found");
        return category;
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
