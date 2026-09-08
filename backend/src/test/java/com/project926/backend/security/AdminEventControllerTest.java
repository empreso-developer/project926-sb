package com.project926.backend.security;

import com.project926.backend.config.SecurityConfig;
import com.project926.backend.controller.AdminEventController;
import com.project926.backend.exception.EventNotFoundException;
import com.project926.backend.exception.ForbiddenException;
import com.project926.backend.exception.GlobalExceptionHandler;
import com.project926.backend.service.EventModerationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.oauth2.jwt.JwtClaimNames.SUB;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers Step 14's ADMIN security requirements at the HTTP layer: no token
 * -> 401, service-layer Forbidden (non-admin caller) -> 403, and that the
 * Clerk identity used to check admin-ness is always the JWT subject.
 */
@WebMvcTest(controllers = AdminEventController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class AdminEventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EventModerationService eventModerationService;

    private static final String ADMIN_ID = "user_admin0000000000000000";

    @Test
    void listAllEvents_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/events")).andExpect(status().isUnauthorized());
    }

    @Test
    void approveEvent_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/admin/events/" + UUID.randomUUID() + "/approve"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void listAllEvents_withValidAdminToken_returns200() throws Exception {
        when(eventModerationService.listAllEventsForAdmin(ADMIN_ID)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/admin/events")
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt -> jwt.subject(ADMIN_ID).claim(SUB, ADMIN_ID))))
            .andExpect(status().isOk());

        verify(eventModerationService).listAllEventsForAdmin(ADMIN_ID);
    }

    @Test
    void listAllEvents_nonAdminCaller_returns403() throws Exception {
        String nonAdminId = "user_notAdmin000000000000";
        when(eventModerationService.listAllEventsForAdmin(nonAdminId))
            .thenThrow(new ForbiddenException("Requires admin role"));

        mockMvc.perform(get("/api/v1/admin/events")
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt -> jwt.subject(nonAdminId).claim(SUB, nonAdminId))))
            .andExpect(status().isForbidden());
    }

    @Test
    void approveEvent_asAdmin_returns204() throws Exception {
        UUID eventId = UUID.randomUUID();
        doNothing().when(eventModerationService).approveEvent(eventId, ADMIN_ID);

        mockMvc.perform(post("/api/v1/admin/events/" + eventId + "/approve")
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt -> jwt.subject(ADMIN_ID).claim(SUB, ADMIN_ID))))
            .andExpect(status().isNoContent());
    }

    @Test
    void approveEvent_nonAdminCaller_returns403() throws Exception {
        UUID eventId = UUID.randomUUID();
        String nonAdminId = "user_notAdmin000000000000";
        doThrow(new ForbiddenException("Requires admin role"))
            .when(eventModerationService).approveEvent(eventId, nonAdminId);

        mockMvc.perform(post("/api/v1/admin/events/" + eventId + "/approve")
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt -> jwt.subject(nonAdminId).claim(SUB, nonAdminId))))
            .andExpect(status().isForbidden());
    }

    @Test
    void rejectEvent_asAdmin_returns204() throws Exception {
        UUID eventId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/admin/events/" + eventId + "/reject")
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt -> jwt.subject(ADMIN_ID).claim(SUB, ADMIN_ID))))
            .andExpect(status().isNoContent());

        verify(eventModerationService).rejectEvent(eventId, ADMIN_ID);
    }

    @Test
    void rejectEvent_nonAdminCaller_returns403() throws Exception {
        UUID eventId = UUID.randomUUID();
        String nonAdminId = "user_notAdmin000000000000";
        doThrow(new ForbiddenException("Requires admin role"))
            .when(eventModerationService).rejectEvent(eventId, nonAdminId);

        mockMvc.perform(post("/api/v1/admin/events/" + eventId + "/reject")
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt -> jwt.subject(nonAdminId).claim(SUB, nonAdminId))))
            .andExpect(status().isForbidden());
    }

    @Test
    void removeEvent_asAdmin_returns204_regardlessOfOwnership() throws Exception {
        UUID eventId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/admin/events/" + eventId)
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt -> jwt.subject(ADMIN_ID).claim(SUB, ADMIN_ID))))
            .andExpect(status().isNoContent());

        verify(eventModerationService).removeEvent(eventId, ADMIN_ID);
    }

    @Test
    void approveEvent_nonexistentEvent_returns404() throws Exception {
        UUID eventId = UUID.randomUUID();
        doThrow(new EventNotFoundException(eventId)).when(eventModerationService).approveEvent(eventId, ADMIN_ID);

        mockMvc.perform(post("/api/v1/admin/events/" + eventId + "/approve")
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt -> jwt.subject(ADMIN_ID).claim(SUB, ADMIN_ID))))
            .andExpect(status().isNotFound());
    }
}
