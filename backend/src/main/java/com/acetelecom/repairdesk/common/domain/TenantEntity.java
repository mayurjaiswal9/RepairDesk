package com.acetelecom.repairdesk.common.domain;

import java.util.UUID;

import com.acetelecom.repairdesk.feature.company.CompanyAssignListener;
import com.acetelecom.repairdesk.feature.company.CompanyScoped;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

@MappedSuperclass
@EntityListeners(CompanyAssignListener.class)
@Getter
@Setter
public abstract class TenantEntity extends BaseEntity implements CompanyScoped {

    @Column(name = "company_id", nullable = false)
    private UUID companyId;
}
