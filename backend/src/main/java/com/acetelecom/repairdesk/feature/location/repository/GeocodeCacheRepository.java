package com.acetelecom.repairdesk.feature.location.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.acetelecom.repairdesk.feature.location.domain.GeocodeCacheEntry;

public interface GeocodeCacheRepository extends JpaRepository<GeocodeCacheEntry, String> {
}
