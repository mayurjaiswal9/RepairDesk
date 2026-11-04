package com.acetelecom.repairdesk.feature.settings.domain;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.Filter;

import com.acetelecom.repairdesk.feature.company.CompanyAssignListener;
import com.acetelecom.repairdesk.feature.company.CompanyFilters;
import com.acetelecom.repairdesk.feature.company.CompanyScoped;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "business_settings")
@EntityListeners(CompanyAssignListener.class)
@Filter(name = CompanyFilters.NAME, condition = CompanyFilters.CONDITION)
@Getter
@Setter
@NoArgsConstructor
public class BusinessSettings implements CompanyScoped {

    @Id
    private Short id = 1;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

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

    @Column(name = "shop_lat")
    private Double shopLat;

    @Column(name = "shop_lng")
    private Double shopLng;

    @Column(name = "geofence_radius_m", nullable = false)
    private int geofenceRadiusM = 150;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Version
    private long version;
}
