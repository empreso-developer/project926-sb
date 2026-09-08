package com.project926.backend.controller;

import com.project926.backend.dto.EventDto;
import com.project926.backend.service.EventService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Public, read-only event endpoints — mirrors the existing public event
 * pages (approved listing + detail-by-id, neither of which requires
 * sign-in today). Permitted without authentication in SecurityConfig.
 */
@RestController
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @GetMapping("/api/v1/events")
    public List<EventDto> listApprovedEvents() {
        return eventService.listApprovedEvents();
    }

    @GetMapping("/api/v1/events/{eventId}")
    public EventDto getEvent(@PathVariable UUID eventId) {
        return eventService.getEventById(eventId);
    }
}
