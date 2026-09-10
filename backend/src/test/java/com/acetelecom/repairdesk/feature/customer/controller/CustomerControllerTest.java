package com.acetelecom.repairdesk.feature.customer.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.acetelecom.repairdesk.feature.customer.service.CustomerService;
import com.acetelecom.repairdesk.feature.job.repository.JobRepository;
import com.acetelecom.repairdesk.feature.request.repository.RepairRequestRepository;
import com.acetelecom.repairdesk.feature.user.domain.Role;
import com.acetelecom.repairdesk.feature.user.repository.UserRepository;

/**
 * BUG-9 regression: a missing {@code q} search param must not be forwarded to
 * the repository as a {@code null} bind parameter, since Hibernate/the
 * PostgreSQL driver can't infer its type inside the repository's
 * {@code concat('%', :q, '%')} expression and defaults it to {@code bytea},
 * causing {@code lower(bytea)} to blow up with a 500.
 */
@ExtendWith(MockitoExtension.class)
class CustomerControllerTest {

    @Mock private UserRepository userRepo;
    @Mock private RepairRequestRepository requestRepo;
    @Mock private JobRepository jobRepo;
    @Mock private CustomerService customerService;

    @Test
    void search_withMissingQuery_passesEmptyStringNotNull() {
        CustomerController controller = new CustomerController(userRepo, requestRepo, jobRepo, customerService);
        when(userRepo.searchCustomers(eq(Role.CUSTOMER), eq(""))).thenReturn(List.of());

        controller.search(null);

        verify(userRepo).searchCustomers(Role.CUSTOMER, "");
    }

    @Test
    void search_withBlankQuery_passesThrough() {
        CustomerController controller = new CustomerController(userRepo, requestRepo, jobRepo, customerService);
        when(userRepo.searchCustomers(eq(Role.CUSTOMER), eq(""))).thenReturn(List.of());

        controller.search("");

        verify(userRepo).searchCustomers(Role.CUSTOMER, "");
    }

    @Test
    void search_withQuery_passesThrough() {
        CustomerController controller = new CustomerController(userRepo, requestRepo, jobRepo, customerService);
        when(userRepo.searchCustomers(eq(Role.CUSTOMER), eq("asheesh"))).thenReturn(List.of());

        controller.search("asheesh");

        verify(userRepo).searchCustomers(Role.CUSTOMER, "asheesh");
    }
}
