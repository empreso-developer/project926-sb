package com.project926.backend.security;

import com.project926.backend.config.SecurityConfig;
import com.project926.backend.controller.EventController;
import com.project926.backend.dto.EventDto;
import com.project926.backend.exception.EventNotFoundException;
import com.project926.backend.exception.GlobalExceptionHandler;
import com.project926.backend.service.EventService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers Step 9/10's event requirements: public access (no token needed —
 * mirrors the existing public event pages), listing shape/ordering as
 * returned by the service, detail lookup, and 404 for a nonexistent event.
 * Visibility/approval-rule fidelity (no status filter on detail) is a
 * property of EventService, exercised directly in EventServiceTest and via
 * the dev-DB integration test — this class only proves the HTTP layer.
 */
@WebMvcTest(controllers = EventController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class EventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EventService eventService;

    private EventDto sampleEvent(UUID id, String title) {
        return new EventDto(
            id,
            "user_organizer00000000000",
            title,
            "description",
            LocalDate.of(2026, 12, 1),
            LocalTime.of(19, 0),
            "Venue",
            "City",
            null,
            "approved",
            OffsetDateTime.now(),
            OffsetDateTime.now(),
            List.of()
        );
    }

    @Test
    void listApprovedEventsIsPublicAndReturnsServiceResult() throws Exception {
        UUID id = UUID.randomUUID();
        when(eventService.listApprovedEvents()).thenReturn(List.of(sampleEvent(id, "Concert")));

        mockMvc.perform(get("/api/v1/events"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(id.toString()))
            .andExpect(jsonPath("$[0].title").value("Concert"))
            .andExpect(jsonPath("$[0].status").value("approved"));
    }

    @Test
    void eventDetailIsPublicAndReturnsServiceResult() throws Exception {
        UUID id = UUID.randomUUID();
        when(eventService.getEventById(id)).thenReturn(sampleEvent(id, "Comedy Night"));

        mockMvc.perform(get("/api/v1/events/" + id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Comedy Night"));
    }

    @Test
    void nonexistentEventReturns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(eventService.getEventById(id)).thenThrow(new EventNotFoundException(id));

        mockMvc.perform(get("/api/v1/events/" + id))
            .andExpect(status().isNotFound());
    }

    @Test
    void malformedEventIdReturns400NotAServerError() throws Exception {
        mockMvc.perform(get("/api/v1/events/not-a-uuid"))
            .andExpect(status().isBadRequest());
    }
}
