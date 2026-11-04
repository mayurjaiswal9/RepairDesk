package com.acetelecom.repairdesk.feature.location.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.acetelecom.repairdesk.feature.location.domain.MapsApiUsage;

public interface MapsApiUsageRepository extends JpaRepository<MapsApiUsage, MapsApiUsage.Key> {
}
