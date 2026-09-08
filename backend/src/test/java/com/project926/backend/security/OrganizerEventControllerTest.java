package com.project926.backend.security;

import com.project926.backend.config.SecurityConfig;
import com.project926.backend.controller.OrganizerEventController;
import com.project926.backend.dto.EventDto;
import com.project926.backend.exception.EventNotFoundException;
import com.project926.backend.exception.ForbiddenException;
import com.project926.backend.exception.GlobalExceptionHandler;
import com.project926.backend.service.EventService;
import com.project926.backend.service.TicketTypeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.oauth2.jwt.JwtClaimNames.SUB;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers Step 14's SECURITY requirements for the organizer endpoints:
 * unauthenticated rejection, that the Clerk identity always comes from the
 * JWT subject (never the request body), and that service-layer
 * Forbidden/NotFound outcomes map to the correct HTTP status.
 */
@WebMvcTest(controllers = OrganizerEventController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class OrganizerEventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EventService eventService;

    @MockBean
    private TicketTypeService ticketTypeService;

    private static final String CALLER_ID = "user_caller00000000000000";

    private EventDto sampleEvent(UUID id) {
        return new EventDto(id, CALLER_ID, "Title", "desc", LocalDate.of(2026, 12, 1),
            LocalTime.of(19, 0), "Venue", "City", null, "draft",
            OffsetDateTime.now(), OffsetDateTime.now(), List.of());
    }

    // Field names are snake_case: spring.jackson.property-naming-strategy is
    // SNAKE_CASE globally (application.yml), which applies to inbound
    // request binding too, not just outbound responses.
    private String validCreateEventJson() {
        return """
            {"title":"Concert","description":"desc","event_date":"2026-12-01","event_time":"19:00:00","venue":"Venue","city":"City"}
            """;
    }

    // ---- Authentication -------------------------------------------------

    @Test
    void listOwnEvents_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/organizer/events")).andExpect(status().isUnauthorized());
    }

    @Test
    void createEvent_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/organizer/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validCreateEventJson()))
            .andExpect(status().isUnauthorized());
    }

    // ---- Identity comes only from the JWT subject ------------------------

    @Test
    void createEvent_usesJwtSubjectAsOrganizer_ignoringAnyBodySuppliedIdentity() throws Exception {
        UUID id = UUID.randomUUID();
        when(eventService.createEvent(eq(CALLER_ID), any())).thenReturn(sampleEvent(id));

        // The request body has no organizerId/id field at all (the DTO
        // doesn't define one) — sending extra unknown fields proves they
        // are simply ignored, not used as an authority source.
        String bodyWithExtraFields = """
            {"title":"Concert","description":"desc","event_date":"2026-12-01","event_time":"19:00:00",
             "venue":"Venue","city":"City","organizer_id":"user_attacker00000000000","role":"admin"}
            """;

        mockMvc.perform(post("/api/v1/organizer/events")
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt -> jwt.subject(CALLER_ID).claim(SUB, CALLER_ID)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(bodyWithExtraFields))
            .andExpect(status().isCreated());

        verify(eventService).createEvent(eq(CALLER_ID), any());
    }

    // ---- Authorization outcomes propagate correctly ----------------------

    @Test
    void createEvent_forbiddenFromService_returns403() throws Exception {
        when(eventService.createEvent(eq(CALLER_ID), any())).thenThrow(new ForbiddenException("Requires organizer or admin role"));

        mockMvc.perform(post("/api/v1/organizer/events")
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt -> jwt.subject(CALLER_ID).claim(SUB, CALLER_ID)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validCreateEventJson()))
            .andExpect(status().isForbidden());
    }

    @Test
    void updateEvent_notFoundFromService_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(eventService.updateEvent(eq(id), eq(CALLER_ID), any())).thenThrow(new EventNotFoundException(id));

        mockMvc.perform(put("/api/v1/organizer/events/" + id)
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt -> jwt.subject(CALLER_ID).claim(SUB, CALLER_ID)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validCreateEventJson()))
            .andExpect(status().isNotFound());
    }

    @Test
    void updateEvent_forbiddenFromService_returns403() throws Exception {
        UUID id = UUID.randomUUID();
        when(eventService.updateEvent(eq(id), eq(CALLER_ID), any())).thenThrow(new ForbiddenException("Not the organizer"));

        mockMvc.perform(put("/api/v1/organizer/events/" + id)
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt -> jwt.subject(CALLER_ID).claim(SUB, CALLER_ID)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validCreateEventJson()))
            .andExpect(status().isForbidden());
    }

    @Test
    void deleteEvent_succeeds_returns204() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/organizer/events/" + id)
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt -> jwt.subject(CALLER_ID).claim(SUB, CALLER_ID))))
            .andExpect(status().isNoContent());

        verify(eventService).deleteEvent(id, CALLER_ID);
    }

    @Test
    void publishEvent_succeeds_returnsUpdatedEvent() throws Exception {
        UUID id = UUID.randomUUID();
        when(eventService.publishEvent(id, CALLER_ID)).thenReturn(sampleEvent(id));

        mockMvc.perform(post("/api/v1/organizer/events/" + id + "/publish")
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt -> jwt.subject(CALLER_ID).claim(SUB, CALLER_ID))))
            .andExpect(status().isOk());
    }

    @Test
    void createTicketType_forbidden_returns403() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(ticketTypeService.createTicketType(eq(eventId), eq(CALLER_ID), any()))
            .thenThrow(new ForbiddenException("Not the organizer"));

        mockMvc.perform(post("/api/v1/organizer/events/" + eventId + "/ticket-types")
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt -> jwt.subject(CALLER_ID).claim(SUB, CALLER_ID)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"General","price":500.00,"quantity_total":100}
                    """))
            .andExpect(status().isForbidden());
    }

    @Test
    void deleteTicketType_succeeds_returns204() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/organizer/events/" + eventId + "/ticket-types/" + ticketTypeId)
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt -> jwt.subject(CALLER_ID).claim(SUB, CALLER_ID))))
            .andExpect(status().isNoContent());

        verify(ticketTypeService).deleteTicketType(eventId, ticketTypeId, CALLER_ID);
    }

    @Test
    void createEvent_withInvalidBody_returns400() throws Exception {
        // title too short (min 3), violates CreateEventRequest validation
        String invalidBody = """
            {"title":"a","event_date":"2026-12-01","event_time":"19:00:00","venue":"Venue","city":"City"}
            """;

        mockMvc.perform(post("/api/v1/organizer/events")
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt -> jwt.subject(CALLER_ID).claim(SUB, CALLER_ID)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidBody))
            .andExpect(status().isBadRequest());
    }
}
