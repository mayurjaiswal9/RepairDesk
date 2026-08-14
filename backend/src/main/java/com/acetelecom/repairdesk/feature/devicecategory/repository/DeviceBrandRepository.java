package com.acetelecom.repairdesk.feature.devicecategory.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.acetelecom.repairdesk.feature.devicecategory.domain.DeviceBrand;

public interface DeviceBrandRepository extends JpaRepository<DeviceBrand, UUID> {

    List<DeviceBrand> findAllByDeviceCategoryIdAndArchivedFalseOrderByNameAsc(UUID categoryId);

    List<DeviceBrand> findAllByDeviceCategoryIdOrderByArchivedAscNameAsc(UUID categoryId);

    Optional<DeviceBrand> findByDeviceCategoryIdAndNameIgnoreCaseAndArchivedFalse(
            UUID categoryId, String name);

    long countByDeviceCategoryIdAndArchivedFalse(UUID categoryId);

    /** [categoryId, activeCount] rows for the Brands column on the categories list. */
    @Query("SELECT b.deviceCategory.id, COUNT(b) FROM DeviceBrand b "
            + "WHERE b.archived = false GROUP BY b.deviceCategory.id")
    List<Object[]> countActiveGroupedByCategory();
}
