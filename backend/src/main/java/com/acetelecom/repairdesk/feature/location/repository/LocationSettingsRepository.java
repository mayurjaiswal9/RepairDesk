package com.acetelecom.repairdesk.feature.location.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.acetelecom.repairdesk.feature.location.domain.LocationSettings;

public interface LocationSettingsRepository extends JpaRepository<LocationSettings, Short> {

    Optional<LocationSettings> findByCompanyId(UUID companyId);
}
