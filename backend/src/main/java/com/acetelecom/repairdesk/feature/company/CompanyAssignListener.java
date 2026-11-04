package com.acetelecom.repairdesk.feature.company;

import java.util.UUID;

import jakarta.persistence.PrePersist;

/** New rows take the signed-in company. A row that already has one is left alone. */
public class CompanyAssignListener {

    @PrePersist
    public void assign(Object entity) {
        if (!(entity instanceof CompanyScoped scoped) || scoped.getCompanyId() != null) {
            return;
        }
        UUID current = CompanyContext.get();
        if (current != null) {
            scoped.setCompanyId(current);
        }
    }
}
