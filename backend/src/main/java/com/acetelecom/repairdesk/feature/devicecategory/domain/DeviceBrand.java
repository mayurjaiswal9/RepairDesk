package com.acetelecom.repairdesk.feature.devicecategory.domain;

import org.hibernate.annotations.Filter;

import com.acetelecom.repairdesk.common.domain.TenantEntity;
import com.acetelecom.repairdesk.feature.company.CompanyFilters;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A brand curated by an admin under a specific {@link DeviceCategory} (PROD-004). */
@Entity
@Table(name = "device_brands")
@Filter(name = CompanyFilters.NAME, condition = CompanyFilters.CONDITION)
@Getter
@Setter
@NoArgsConstructor
public class DeviceBrand extends TenantEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_category_id", nullable = false)
    private DeviceCategory deviceCategory;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private boolean archived = false;
}
