package com.acetelecom.repairdesk.feature.devicecategory.domain;

import com.acetelecom.repairdesk.common.domain.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "device_categories")
@Getter
@Setter
@NoArgsConstructor
public class DeviceCategory extends BaseEntity {

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    private boolean archived = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "brand_mode", nullable = false, length = 20)
    private BrandMode brandMode = BrandMode.FREE_TEXT;
}
