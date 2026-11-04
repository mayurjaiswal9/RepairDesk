package com.acetelecom.repairdesk.feature.company;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.acetelecom.repairdesk.config.AppProperties;
import com.acetelecom.repairdesk.feature.company.domain.Company;
import com.acetelecom.repairdesk.feature.company.domain.CompanyStatus;
import com.acetelecom.repairdesk.feature.user.domain.AccountStatus;
import com.acetelecom.repairdesk.feature.user.domain.Role;
import com.acetelecom.repairdesk.feature.user.domain.User;
import com.acetelecom.repairdesk.feature.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;

/**
 * A second company exists only in this thrown-away test database.
 * Blank company code still signs in to ACE Telecom.
 */
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@ActiveProfiles("test")
class CompanyIsolationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AppProperties appProperties;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Test
    void blankCodeIsAceAndAnotherCompanyCannotSeeAceUsers() throws Exception {
        Company beta = new Company();
        beta.setId(UUID.randomUUID());
        beta.setCode("beta");
        beta.setName("Beta Shop");
        beta.setStatus(CompanyStatus.ACTIVE);
        beta.setCreatedAt(Instant.now());
        companyRepository.save(beta);

        User betaAdmin = new User();
        betaAdmin.setCompanyId(beta.getId());
        betaAdmin.setUsername("beta.admin");
        betaAdmin.setName("Beta Admin");
        betaAdmin.setMobile("9000000001");
        betaAdmin.setPasswordHash(passwordEncoder.encode("BetaPass1"));
        betaAdmin.setRole(Role.ADMIN);
        betaAdmin.setStatus(AccountStatus.ACTIVE);
        userRepository.save(betaAdmin);

        String acePassword = appProperties.getAdmin().getDefaultPassword();
        String aceName = appProperties.getAdmin().getDefaultUsername();

        String aceToken = login(aceName, acePassword, null);
        String betaToken = login("beta.admin", "BetaPass1", "Beta");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(aceName, acePassword, "beta")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("beta.admin", "BetaPass1", null)))
                .andExpect(status().isUnauthorized());

        String aceUsers = mockMvc.perform(get("/users")
                        .header("Authorization", "Bearer " + aceToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String betaUsers = mockMvc.perform(get("/users")
                        .header("Authorization", "Bearer " + betaToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(aceUsers).contains("\"username\":\"" + aceName + "\"");
        assertThat(aceUsers).doesNotContain("\"username\":\"beta.admin\"");
        assertThat(betaUsers).contains("\"username\":\"beta.admin\"");
        assertThat(betaUsers).doesNotContain("\"username\":\"" + aceName + "\"");
    }

    private String login(String username, String password, String companyCode) throws Exception {
        String json = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(username, password, companyCode)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode token = objectMapper.readTree(json).get("token");
        assertThat(token).isNotNull();
        return token.asText();
    }

    private static String body(String username, String password, String companyCode) {
        if (companyCode == null) {
            return "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
        }
        return "{\"username\":\"" + username + "\",\"password\":\"" + password
                + "\",\"companyCode\":\"" + companyCode + "\"}";
    }
}
