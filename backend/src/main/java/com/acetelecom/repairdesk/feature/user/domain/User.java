package com.acetelecom.repairdesk.feature.user.domain;

import org.hibernate.annotations.Filter;

import com.acetelecom.repairdesk.common.domain.TenantEntity;
import com.acetelecom.repairdesk.feature.company.CompanyFilters;
import com.acetelecom.repairdesk.feature.location.domain.VehicleType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "users")
@Filter(name = CompanyFilters.NAME, condition = CompanyFilters.CONDITION)
@Getter
@Setter
@NoArgsConstructor
public class User extends TenantEntity {

    @Column(nullable = false, length = 50)
    private String username;

    @Column(nullable = false, length = 20)
    private String mobile;

    @Column(length = 100)
    private String name;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountStatus status = AccountStatus.ACTIVE;

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", length = 20)
    private VehicleType vehicleType;

    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }
}
