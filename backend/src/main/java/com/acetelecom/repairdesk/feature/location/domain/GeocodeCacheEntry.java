package com.acetelecom.repairdesk.feature.location.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "geocode_cache")
@Getter
@Setter
@NoArgsConstructor
public class GeocodeCacheEntry {

    @Id
    @Column(name = "address_hash", length = 64)
    private String addressHash;

    @Column(name = "normalized_address", nullable = false, length = 500)
    private String normalizedAddress;

    @Column(nullable = false)
    private double lat;

    @Column(nullable = false)
    private double lng;

    @Column(name = "formatted_address", length = 500)
    private String formattedAddress;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
