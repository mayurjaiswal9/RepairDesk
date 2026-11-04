package com.acetelecom.repairdesk.feature.company;

import java.util.UUID;

public interface CompanyScoped {

    UUID getCompanyId();

    void setCompanyId(UUID companyId);
}
