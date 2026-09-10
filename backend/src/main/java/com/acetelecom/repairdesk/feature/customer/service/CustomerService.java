package com.acetelecom.repairdesk.feature.customer.service;

import java.security.SecureRandom;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acetelecom.repairdesk.common.error.ApiException;
import com.acetelecom.repairdesk.common.error.ErrorCode;
import com.acetelecom.repairdesk.feature.customer.dto.CreateCustomerRequest;
import com.acetelecom.repairdesk.feature.customer.dto.CreateCustomerResponse;
import com.acetelecom.repairdesk.feature.user.domain.AccountStatus;
import com.acetelecom.repairdesk.feature.user.domain.Role;
import com.acetelecom.repairdesk.feature.user.domain.User;
import com.acetelecom.repairdesk.feature.user.repository.UserRepository;

@Service
@Transactional
public class CustomerService {

    private static final String PASSWORD_CHARS =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789#@$%";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepo;
    private final PasswordEncoder passwordEncoder;

    public CustomerService(UserRepository userRepo, PasswordEncoder passwordEncoder) {
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
    }

    public CreateCustomerResponse createWalkIn(CreateCustomerRequest req) {
        String mobile = req.mobile().replaceAll("[\\s\\-]", "");
        String providedPassword = req.password() == null ? "" : req.password().strip();
        if (!providedPassword.isEmpty() && providedPassword.length() < 8) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Password must be at least 8 characters");
        }

        userRepo.findActiveByMobile(mobile).ifPresent(existing -> {
            throw conflict("Mobile already registered", existing);
        });

        String username = deriveUsername(req.username(), mobile);
        userRepo.findActiveByUsername(username).ifPresent(existing -> {
            throw conflict("Username already taken", existing);
        });

        boolean generated = providedPassword.isEmpty();
        String rawPassword = generated ? generatePassword() : providedPassword;

        User user = new User();
        user.setName(req.name().strip());
        user.setMobile(mobile);
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setRole(Role.CUSTOMER);
        user.setStatus(AccountStatus.ACTIVE);
        user.setMustChangePassword(generated);
        user = userRepo.save(user);

        return CreateCustomerResponse.from(user, generated ? rawPassword : null);
    }

    private String deriveUsername(String requested, String mobile) {
        String base = requested == null ? "" : requested.strip();
        if (base.isEmpty()) {
            String digits = mobile.replaceAll("\\D", "");
            base = digits.isEmpty() ? "c" + UUID.randomUUID().toString().substring(0, 8) : digits;
        }
        String candidate = base;
        int n = 2;
        while (userRepo.findActiveByUsername(candidate).isPresent()) {
            candidate = base + n;
            n++;
        }
        return candidate;
    }

    private static String generatePassword() {
        StringBuilder sb = new StringBuilder(10);
        for (int i = 0; i < 10; i++) {
            sb.append(PASSWORD_CHARS.charAt(RANDOM.nextInt(PASSWORD_CHARS.length())));
        }
        return sb.toString();
    }

    private static ApiException conflict(String message, User existing) {
        if (existing.getRole() == Role.CUSTOMER) {
            return new ApiException(ErrorCode.CONFLICT, message,
                    Map.of("existingCustomerId", existing.getId()));
        }
        return new ApiException(ErrorCode.CONFLICT, message);
    }
}
