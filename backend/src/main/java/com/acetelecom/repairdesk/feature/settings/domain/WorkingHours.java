package com.acetelecom.repairdesk.feature.settings.domain;

import java.time.LocalTime;
import java.util.UUID;

import org.hibernate.annotations.Filter;

import com.acetelecom.repairdesk.feature.company.CompanyAssignListener;
import com.acetelecom.repairdesk.feature.company.CompanyFilters;
import com.acetelecom.repairdesk.feature.company.CompanyScoped;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "working_hours")
@EntityListeners(CompanyAssignListener.class)
@Filter(name = CompanyFilters.NAME, condition = CompanyFilters.CONDITION)
@Getter
@Setter
@NoArgsConstructor
public class WorkingHours implements CompanyScoped {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "day_of_week", nullable = false)
    private short dayOfWeek; // 0=Mon .. 6=Sun

    @Column(name = "is_closed", nullable = false)
    private boolean closed;

    @Column(name = "open_time")
    private LocalTime openTime;

    @Column(name = "close_time")
    private LocalTime closeTime;

    @Version
    private long version;
}
