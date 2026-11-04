package com.acetelecom.repairdesk.config;

import java.util.UUID;

import org.hibernate.Session;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.acetelecom.repairdesk.feature.company.CompanyContext;
import com.acetelecom.repairdesk.feature.company.CompanyFilters;

import jakarta.persistence.EntityManagerFactory;

/**
 * Turns on the company filter for a signed-in request. Schedulers have no
 * company on the thread, so they still see every open attendance day.
 */
public class CompanyTransactionManager extends JpaTransactionManager {

    public CompanyTransactionManager(EntityManagerFactory emf) {
        super(emf);
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        super.doBegin(transaction, definition);
        UUID companyId = CompanyContext.get();
        if (companyId == null) {
            return;
        }
        Object resource = TransactionSynchronizationManager.getResource(obtainEntityManagerFactory());
        if (resource instanceof EntityManagerHolder holder && holder.getEntityManager() != null) {
            holder.getEntityManager().unwrap(Session.class)
                    .enableFilter(CompanyFilters.NAME)
                    .setParameter("companyId", companyId);
        }
    }
}
