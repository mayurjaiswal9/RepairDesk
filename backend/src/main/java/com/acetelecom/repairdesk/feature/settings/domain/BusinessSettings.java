package com.acetelecom.repairdesk.feature.settings.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "business_settings")
@Getter
@Setter
@NoArgsConstructor
public class BusinessSettings {

    @Id
    private Short id = 1;

    @Column(name = "business_name", length = 150)
    private String businessName;

    @Column(name = "business_address", length = 500)
    private String businessAddress;

    @Column(name = "business_phone", length = 20)
    private String businessPhone;

    @Column(name = "business_email", length = 255)
    private String businessEmail;

    @Column(name = "slot_duration_minutes", nullable = false)
    private int slotDurationMinutes = 60;

    @Column(name = "booking_horizon_days", nullable = false)
    private int bookingHorizonDays = 60;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Version
    private long version;
}
