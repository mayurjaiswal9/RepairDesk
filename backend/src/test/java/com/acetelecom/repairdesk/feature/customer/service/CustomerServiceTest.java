package com.acetelecom.repairdesk.feature.customer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.acetelecom.repairdesk.common.error.ApiException;
import com.acetelecom.repairdesk.feature.customer.dto.CreateCustomerRequest;
import com.acetelecom.repairdesk.feature.user.domain.AccountStatus;
import com.acetelecom.repairdesk.feature.user.domain.Role;
import com.acetelecom.repairdesk.feature.user.domain.User;
import com.acetelecom.repairdesk.feature.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock private UserRepository userRepo;
    @Mock private PasswordEncoder encoder;

    private CustomerService service;

    @BeforeEach
    void setUp() {
        service = new CustomerService(userRepo, encoder);
        org.mockito.Mockito.lenient().when(encoder.encode(anyString())).thenReturn("hashed");
        org.mockito.Mockito.lenient().when(userRepo.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            if (u.getId() == null) {
                u.setId(UUID.randomUUID());
            }
            return u;
        });
    }

    @Test
    void createWalkIn_generatesUsernameAndPassword() {
        when(userRepo.findActiveByMobile(anyString())).thenReturn(Optional.empty());
        when(userRepo.findActiveByUsername(anyString())).thenReturn(Optional.empty());

        var res = service.createWalkIn(new CreateCustomerRequest("Ravi Nair", "91234 56780", null, null));

        assertThat(res.username()).isEqualTo("9123456780");
        assertThat(res.role()).isEqualTo("CUSTOMER");
        assertThat(res.mustChangePassword()).isTrue();
        assertThat(res.generatedPassword()).isNotBlank();
        assertThat(res.generatedPassword()).hasSize(10);
    }

    @Test
    void createWalkIn_duplicateMobile_includesExistingId() {
        User existing = new User();
        existing.setId(UUID.randomUUID());
        existing.setRole(Role.CUSTOMER);
        existing.setStatus(AccountStatus.ACTIVE);
        when(userRepo.findActiveByMobile("9123456780")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() ->
                service.createWalkIn(new CreateCustomerRequest("Ravi", "91234 56780", null, null)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getDetails()).containsEntry("existingCustomerId", existing.getId());
                });
    }
}
