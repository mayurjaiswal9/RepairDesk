package com.acetelecom.repairdesk.feature.company;

/** Hibernate filter that keeps a signed-in request inside one company. */
public final class CompanyFilters {

    public static final String NAME = "companyFilter";
    public static final String CONDITION = "company_id = :companyId";

    private CompanyFilters() {
    }
}
