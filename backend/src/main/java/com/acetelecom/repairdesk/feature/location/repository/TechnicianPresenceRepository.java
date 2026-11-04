package com.acetelecom.repairdesk.feature.location.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.acetelecom.repairdesk.feature.location.domain.TechnicianPresence;

public interface TechnicianPresenceRepository extends JpaRepository<TechnicianPresence, UUID> {
}
