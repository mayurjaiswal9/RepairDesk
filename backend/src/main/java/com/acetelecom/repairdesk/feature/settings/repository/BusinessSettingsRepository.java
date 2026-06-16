package com.acetelecom.repairdesk.feature.settings.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.acetelecom.repairdesk.feature.settings.domain.BusinessSettings;

public interface BusinessSettingsRepository extends JpaRepository<BusinessSettings, Short> {
}
