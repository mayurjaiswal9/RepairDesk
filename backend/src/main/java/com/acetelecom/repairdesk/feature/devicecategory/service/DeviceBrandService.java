package com.acetelecom.repairdesk.feature.devicecategory.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acetelecom.repairdesk.common.error.ApiException;
import com.acetelecom.repairdesk.common.error.ErrorCode;
import com.acetelecom.repairdesk.feature.company.CompanyContext;
import com.acetelecom.repairdesk.feature.devicecategory.domain.DeviceBrand;
import com.acetelecom.repairdesk.feature.devicecategory.domain.DeviceCategory;
import com.acetelecom.repairdesk.feature.devicecategory.dto.DeviceBrandRequest;
import com.acetelecom.repairdesk.feature.devicecategory.dto.DeviceBrandResponse;
import com.acetelecom.repairdesk.feature.devicecategory.repository.DeviceBrandRepository;
import com.acetelecom.repairdesk.feature.devicecategory.repository.DeviceCategoryRepository;

@Service
@Transactional
public class DeviceBrandService {

    private final DeviceBrandRepository brandRepo;
    private final DeviceCategoryRepository categoryRepo;

    public DeviceBrandService(DeviceBrandRepository brandRepo, DeviceCategoryRepository categoryRepo) {
        this.brandRepo = brandRepo;
        this.categoryRepo = categoryRepo;
    }

    @Transactional(readOnly = true)
    public List<DeviceBrandResponse> listActive(UUID categoryId) {
        requireCategory(categoryId);
        return brandRepo.findAllByDeviceCategoryIdAndArchivedFalseOrderByNameAsc(categoryId)
                .stream().map(DeviceBrandResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<DeviceBrandResponse> listAll(UUID categoryId) {
        requireCategory(categoryId);
        return brandRepo.findAllByDeviceCategoryIdOrderByArchivedAscNameAsc(categoryId)
                .stream().map(DeviceBrandResponse::from).toList();
    }

    public DeviceBrandResponse create(UUID categoryId, DeviceBrandRequest req) {
        DeviceCategory category = requireCategory(categoryId);
        checkNameUnique(categoryId, req.name(), null);
        DeviceBrand brand = new DeviceBrand();
        brand.setDeviceCategory(category);
        brand.setName(req.name().strip());
        return DeviceBrandResponse.from(brandRepo.save(brand));
    }

    public DeviceBrandResponse update(UUID categoryId, UUID brandId, DeviceBrandRequest req) {
        DeviceBrand brand = require(categoryId, brandId);
        if (!brand.getName().equalsIgnoreCase(req.name())) {
            checkNameUnique(categoryId, req.name(), brandId);
        }
        brand.setName(req.name().strip());
        brand.setUpdatedAt(Instant.now());
        return DeviceBrandResponse.from(brandRepo.save(brand));
    }

    public void archive(UUID categoryId, UUID brandId) {
        DeviceBrand brand = require(categoryId, brandId);
        brand.setArchived(true);
        brand.setUpdatedAt(Instant.now());
        brandRepo.save(brand);
    }

    public void unarchive(UUID categoryId, UUID brandId) {
        DeviceBrand brand = require(categoryId, brandId);
        checkNameUnique(categoryId, brand.getName(), brandId);
        brand.setArchived(false);
        brand.setUpdatedAt(Instant.now());
        brandRepo.save(brand);
    }

    private DeviceCategory requireCategory(UUID categoryId) {
        DeviceCategory category = categoryRepo.findById(categoryId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Device category not found"));
        CompanyContext.requireSame(category.getCompanyId(), "Device category not found");
        return category;
    }

    private DeviceBrand require(UUID categoryId, UUID brandId) {
        DeviceBrand brand = brandRepo.findById(brandId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Brand not found"));
        CompanyContext.requireSame(brand.getCompanyId(), "Brand not found");
        if (!brand.getDeviceCategory().getId().equals(categoryId)) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Brand not found");
        }
        return brand;
    }

    private void checkNameUnique(UUID categoryId, String name, UUID excludeId) {
        brandRepo.findByDeviceCategoryIdAndNameIgnoreCaseAndArchivedFalse(categoryId, name)
                .ifPresent(existing -> {
                    if (!existing.getId().equals(excludeId)) {
                        throw new ApiException(ErrorCode.CONFLICT,
                                "An active brand with this name already exists for this category");
                    }
                });
    }
}
