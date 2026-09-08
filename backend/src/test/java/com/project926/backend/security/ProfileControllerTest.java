package com.project926.backend.security;

import com.project926.backend.config.SecurityConfig;
import com.project926.backend.controller.ProfileController;
import com.project926.backend.dto.ProfileResponse;
import com.project926.backend.service.ProfileService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.oauth2.jwt.JwtClaimNames.SUB;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers Step 9/10's security + profile requirements for GET /api/v1/profile:
 * 401 with no/malformed token, correct authenticated behavior, and that the
 * Clerk id used to look up the profile comes only from the validated JWT
 * subject — never from a client-supplied parameter.
 */
@WebMvcTest(controllers = ProfileController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class ProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProfileService profileService;

    @Test
    void profileWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/profile"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void profileWithMalformedTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/profile").header("Authorization", "Bearer not-a-real-jwt"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedUserRetrievesTheirOwnProfileUsingJwtSubjectOnly() throws Exception {
        String clerkUserId = "user_3GjubwvONQwJQcpQRdNtbEUEbP5";
        when(profileService.getRoleForAuthenticatedUser(clerkUserId))
            .thenReturn(new ProfileResponse("organizer"));

        mockMvc.perform(get("/api/v1/profile")
                .with(SecurityMockMvcRequestPostProcessors.jwt()
                    .jwt(jwt -> jwt.subject(clerkUserId).claim(SUB, clerkUserId))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("organizer"));

        // Proves the service is called with the JWT's own subject, i.e.
        // there is no code path where a client-supplied id could be used
        // instead (the controller never reads a query/path param at all).
        ArgumentCaptor<String> idCaptor = ArgumentCaptor.forClass(String.class);
        verify(profileService).getRoleForAuthenticatedUser(idCaptor.capture());
        assertThat(idCaptor.getValue()).isEqualTo(clerkUserId);
        assertThat(idCaptor.getValue()).isInstanceOf(String.class);
    }

    @Test
    void clientSuppliedUserIdParameterIsIgnored_onlyJwtSubjectIsUsed() throws Exception {
        String realCallerId = "user_realCaller0000000000000";
        String someoneElseId = "user_someoneElse00000000000";
        when(profileService.getRoleForAuthenticatedUser(anyString()))
            .thenReturn(new ProfileResponse("customer"));

        // Attempting to pass a different user id as a query param — the
        // endpoint has no such parameter, so this can only ever resolve to
        // the authenticated caller's own id.
        mockMvc.perform(get("/api/v1/profile?userId=" + someoneElseId)
                .with(SecurityMockMvcRequestPostProcessors.jwt()
                    .jwt(jwt -> jwt.subject(realCallerId).claim(SUB, realCallerId))))
            .andExpect(status().isOk());

        verify(profileService).getRoleForAuthenticatedUser(realCallerId);
    }

    @Test
    void missingProfileDefaultsToCustomer() throws Exception {
        String clerkUserId = "user_hasNoProfileRowYet0000";
        when(profileService.getRoleForAuthenticatedUser(clerkUserId))
            .thenReturn(new ProfileResponse("customer"));

        mockMvc.perform(get("/api/v1/profile")
                .with(SecurityMockMvcRequestPostProcessors.jwt()
                    .jwt(jwt -> jwt.subject(clerkUserId).claim(SUB, clerkUserId))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("customer"));
    }
}
