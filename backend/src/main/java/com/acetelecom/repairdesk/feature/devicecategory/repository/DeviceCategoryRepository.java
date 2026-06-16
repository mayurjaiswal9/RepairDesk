package com.acetelecom.repairdesk.feature.devicecategory.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.acetelecom.repairdesk.feature.devicecategory.domain.DeviceCategory;

public interface DeviceCategoryRepository extends JpaRepository<DeviceCategory, UUID> {

    List<DeviceCategory> findAllByArchivedFalseOrderByNameAsc();

    List<DeviceCategory> findAllByOrderByArchivedAscNameAsc();

    Optional<DeviceCategory> findByNameIgnoreCaseAndArchivedFalse(String name);
}
