package com.project926.backend.security;

import com.project926.backend.config.SecurityConfig;
import com.project926.backend.controller.HealthController;
import com.project926.backend.controller.ProtectedTestController;
import com.project926.backend.repository.ProfileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.oauth2.jwt.JwtClaimNames.SUB;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pure web+security slice: no DataSource, no real Clerk network call. Proves
 * the authorization rules in SecurityConfig without needing dev database or
 * Clerk credentials, so it always runs as part of `mvn test`.
 */
@WebMvcTest(controllers = {HealthController.class, ProtectedTestController.class})
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProfileRepository profileRepository;

    @Test
    void publicHealthEndpointIsAccessibleWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void protectedEndpointRejectsRequestWithNoToken() throws Exception {
        mockMvc.perform(get("/api/v1/test/protected"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointRejectsMalformedBearerToken() throws Exception {
        mockMvc.perform(get("/api/v1/test/protected")
                .header("Authorization", "Bearer not-a-real-jwt"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointAcceptsValidJwtAndSurfacesClerkSubjectAsString() throws Exception {
        // Simulates what happens once Spring Security has already validated
        // a real Clerk JWT (signature/issuer/expiry) — proves the downstream
        // handling of the subject claim, which is where the "String not
        // UUID" requirement actually matters. The Clerk-specific signature
        // validation itself is exercised by SecurityConfig's real
        // NimbusJwtDecoder wiring against application-dev.yml's jwk-set-uri,
        // not re-tested here with a fake key pair.
        String clerkUserId = "user_3GjubwvONQwJQcpQRdNtbEUEbP5";
        mockMvc.perform(get("/api/v1/test/protected")
                .with(SecurityMockMvcRequestPostProcessors.jwt()
                    .jwt(jwt -> jwt.subject(clerkUserId).claim(SUB, clerkUserId))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.clerkUserId").value(clerkUserId))
            .andExpect(jsonPath("$.clerkUserIdType").value("String"));
    }
}
