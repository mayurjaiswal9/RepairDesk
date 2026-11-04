package com.acetelecom.repairdesk.feature.company.domain;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

import com.acetelecom.repairdesk.feature.company.CompanyFilters;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "companies")
@FilterDef(name = CompanyFilters.NAME, parameters = @ParamDef(name = "companyId", type = UUID.class))
@Getter
@Setter
@NoArgsConstructor
public class Company {

    @Id
    private UUID id;

    @Column(nullable = false, length = 40)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CompanyStatus status = CompanyStatus.ACTIVE;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
