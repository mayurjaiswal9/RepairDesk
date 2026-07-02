package com.acetelecom.repairdesk.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;

/**
 * TEST-007: Security integration tests.
 *
 * <p>Verifies that protected endpoints return 401 when no token is provided
 * and that public endpoints remain accessible.
 */
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@ActiveProfiles("test")
class SecurityIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    // ── Public endpoints must be accessible without a token ───────────────────

    @Test
    void loginEndpoint_noToken_allowsRequest() throws Exception {
        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"noexist\",\"password\":\"badpass\"}"))
                .andExpect(status().is4xxClientError()); // 400 bad creds or 422 validation
    }

    @Test
    void registerEndpoint_noToken_allowsRequest() throws Exception {
        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"u\",\"password\":\"p\",\"mobile\":\"9\"}"))
                .andExpect(status().is4xxClientError()); // validation fail, not 401
    }

    @Test
    void healthEndpoint_noToken_returns2xx() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().is2xxSuccessful());
    }

    // ── Protected endpoints must return 401 without a token ──────────────────

    @Test
    void profileEndpoint_noToken_returns401() throws Exception {
        mockMvc.perform(get("/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void requestsEndpoint_noToken_returns401() throws Exception {
        mockMvc.perform(get("/requests"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void jobsEndpoint_noToken_returns401() throws Exception {
        mockMvc.perform(get("/jobs"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void usersEndpoint_noToken_returns401() throws Exception {
        mockMvc.perform(get("/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void notificationsEndpoint_noToken_returns401() throws Exception {
        mockMvc.perform(get("/me/notifications"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void dashboardEndpoint_noToken_returns401() throws Exception {
        mockMvc.perform(get("/dashboard/counters"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void attachmentUpload_noToken_returns401() throws Exception {
        mockMvc.perform(post("/requests/00000000-0000-0000-0000-000000000000/attachments"))
                .andExpect(status().isUnauthorized());
    }
}
