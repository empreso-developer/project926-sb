package com.project926.backend.security;

import com.project926.backend.config.SecurityConfig;
import com.project926.backend.controller.AdminUserController;
import com.project926.backend.dto.AdminRevenueDto;
import com.project926.backend.dto.AdminUserDto;
import com.project926.backend.exception.ForbiddenException;
import com.project926.backend.exception.GlobalExceptionHandler;
import com.project926.backend.exception.ProfileNotFoundException;
import com.project926.backend.service.AdminUserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.oauth2.jwt.JwtClaimNames.SUB;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers GET /api/v1/admin/users, GET /api/v1/admin/revenue, and
 * PATCH /api/v1/admin/users/{id}/role's security/HTTP-layer requirements:
 * no token -> 401, service-layer Forbidden -> 403, and that the target
 * profile id for role updates comes only from the path, never from the
 * caller's own identity.
 */
@WebMvcTest(controllers = AdminUserController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class AdminUserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminUserService adminUserService;

    private static final String ADMIN_ID = "user_admin0000000000000000";

    // ---- listAllUsers ----

    @Test
    void listUsers_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users")).andExpect(status().isUnauthorized());
    }

    @Test
    void listUsers_asAdmin_returns200() throws Exception {
        AdminUserDto dto = new AdminUserDto("user_x", "x@example.com", "X", "Y", "customer", OffsetDateTime.now());
        when(adminUserService.listAllUsers(ADMIN_ID)).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/v1/admin/users")
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt -> jwt.subject(ADMIN_ID).claim(SUB, ADMIN_ID))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].email").value("x@example.com"))
            .andExpect(jsonPath("$[0].role").value("customer"));
    }

    @Test
    void listUsers_nonAdminCaller_returns403() throws Exception {
        String customerId = "user_customer000000000000";
        when(adminUserService.listAllUsers(customerId)).thenThrow(new ForbiddenException("Requires admin role"));

        mockMvc.perform(get("/api/v1/admin/users")
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt -> jwt.subject(customerId).claim(SUB, customerId))))
            .andExpect(status().isForbidden());
    }

    @Test
    void listUsers_organizerCaller_returns403() throws Exception {
        String organizerId = "user_organizer00000000000";
        when(adminUserService.listAllUsers(organizerId)).thenThrow(new ForbiddenException("Requires admin role"));

        mockMvc.perform(get("/api/v1/admin/users")
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt -> jwt.subject(organizerId).claim(SUB, organizerId))))
            .andExpect(status().isForbidden());
    }

    @Test
    void listUsers_zeroUsers_returnsEmptyArray() throws Exception {
        when(adminUserService.listAllUsers(ADMIN_ID)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/admin/users")
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt -> jwt.subject(ADMIN_ID).claim(SUB, ADMIN_ID))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$").isEmpty());
    }

    // ---- revenue ----

    @Test
    void revenue_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/revenue")).andExpect(status().isUnauthorized());
    }

    @Test
    void revenue_asAdmin_returns200() throws Exception {
        when(adminUserService.getPlatformRevenue(ADMIN_ID)).thenReturn(new AdminRevenueDto(new BigDecimal("2500.50")));

        mockMvc.perform(get("/api/v1/admin/revenue")
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt -> jwt.subject(ADMIN_ID).claim(SUB, ADMIN_ID))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total_revenue").value(2500.50));
    }

    @Test
    void revenue_nonAdminCaller_returns403() throws Exception {
        String customerId = "user_customer000000000000";
        when(adminUserService.getPlatformRevenue(customerId)).thenThrow(new ForbiddenException("Requires admin role"));

        mockMvc.perform(get("/api/v1/admin/revenue")
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt -> jwt.subject(customerId).claim(SUB, customerId))))
            .andExpect(status().isForbidden());
    }

    @Test
    void revenue_zeroQualifyingBookings_returnsZero() throws Exception {
        when(adminUserService.getPlatformRevenue(ADMIN_ID)).thenReturn(new AdminRevenueDto(BigDecimal.ZERO));

        mockMvc.perform(get("/api/v1/admin/revenue")
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt -> jwt.subject(ADMIN_ID).claim(SUB, ADMIN_ID))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total_revenue").value(0));
    }

    // ---- role update ----

    @Test
    void updateRole_withoutToken_returns401() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/users/user_target/role")
                .contentType("application/json")
                .content("{\"role\":\"organizer\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void updateRole_asAdmin_returns204() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/users/user_target00000000000000/role")
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt -> jwt.subject(ADMIN_ID).claim(SUB, ADMIN_ID)))
                .contentType("application/json")
                .content("{\"role\":\"organizer\"}"))
            .andExpect(status().isNoContent());

        verify(adminUserService).updateUserRole("user_target00000000000000", "organizer", ADMIN_ID);
    }

    @Test
    void updateRole_nonAdminCaller_returns403() throws Exception {
        String customerId = "user_customer000000000000";
        doThrow(new ForbiddenException("Requires admin role"))
            .when(adminUserService).updateUserRole(anyString(), anyString(), org.mockito.ArgumentMatchers.eq(customerId));

        mockMvc.perform(patch("/api/v1/admin/users/user_target00000000000000/role")
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt -> jwt.subject(customerId).claim(SUB, customerId)))
                .contentType("application/json")
                .content("{\"role\":\"admin\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void updateRole_organizerCaller_returns403() throws Exception {
        String organizerId = "user_organizer00000000000";
        doThrow(new ForbiddenException("Requires admin role"))
            .when(adminUserService).updateUserRole(anyString(), anyString(), org.mockito.ArgumentMatchers.eq(organizerId));

        mockMvc.perform(patch("/api/v1/admin/users/user_target00000000000000/role")
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt -> jwt.subject(organizerId).claim(SUB, organizerId)))
                .contentType("application/json")
                .content("{\"role\":\"admin\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void updateRole_invalidRoleValue_returns400_andNeverCallsService() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/users/user_target00000000000000/role")
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt -> jwt.subject(ADMIN_ID).claim(SUB, ADMIN_ID)))
                .contentType("application/json")
                .content("{\"role\":\"superadmin\"}"))
            .andExpect(status().isBadRequest());

        verify(adminUserService, org.mockito.Mockito.never()).updateUserRole(anyString(), anyString(), anyString());
    }

    @Test
    void updateRole_blankRoleValue_returns400() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/users/user_target00000000000000/role")
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt -> jwt.subject(ADMIN_ID).claim(SUB, ADMIN_ID)))
                .contentType("application/json")
                .content("{\"role\":\"\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void updateRole_nonexistentTarget_returns404() throws Exception {
        doThrow(new ProfileNotFoundException("user_doesNotExist00000000"))
            .when(adminUserService).updateUserRole("user_doesNotExist00000000", "admin", ADMIN_ID);

        mockMvc.perform(patch("/api/v1/admin/users/user_doesNotExist00000000/role")
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt -> jwt.subject(ADMIN_ID).claim(SUB, ADMIN_ID)))
                .contentType("application/json")
                .content("{\"role\":\"admin\"}"))
            .andExpect(status().isNotFound());
    }

    @Test
    void updateRole_cannotBypassAuthorizationBySupplyingAnotherCallerIdInBody() throws Exception {
        // The request body/path has no "callerId"/"adminId" field at all —
        // the authenticated identity always comes from the JWT subject.
        // Supplying an unrelated JSON field is simply ignored.
        mockMvc.perform(patch("/api/v1/admin/users/user_target00000000000000/role")
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt -> jwt.subject(ADMIN_ID).claim(SUB, ADMIN_ID)))
                .contentType("application/json")
                .content("{\"role\":\"organizer\",\"adminId\":\"user_someoneElse000000000\",\"callerId\":\"user_someoneElse000000000\"}"))
            .andExpect(status().isNoContent());

        verify(adminUserService).updateUserRole("user_target00000000000000", "organizer", ADMIN_ID);
    }
}
