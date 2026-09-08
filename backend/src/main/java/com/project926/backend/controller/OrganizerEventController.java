package com.project926.backend.controller;

import com.project926.backend.dto.CreateEventRequest;
import com.project926.backend.dto.CreateTicketTypeRequest;
import com.project926.backend.dto.EventDto;
import com.project926.backend.dto.TicketTypeDto;
import com.project926.backend.dto.UpdateEventRequest;
import com.project926.backend.service.EventService;
import com.project926.backend.service.TicketTypeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Mirrors lib/actions/events.ts (createEventAction / updateEventAction /
 * publishEventAction / deleteEventAction / createTicketTypeAction /
 * deleteTicketTypeAction) and the organizer dashboard's own-events reads.
 * All authorization (organizer/admin role, then strict event ownership) is
 * enforced in EventService/TicketTypeService — see those classes for the
 * exact rules being reproduced. The Clerk id used everywhere here comes
 * only from the validated JWT subject, never from the request body/path.
 */
@RestController
@RequestMapping("/api/v1/organizer/events")
public class OrganizerEventController {

    private final EventService eventService;
    private final TicketTypeService ticketTypeService;

    public OrganizerEventController(EventService eventService, TicketTypeService ticketTypeService) {
        this.eventService = eventService;
        this.ticketTypeService = ticketTypeService;
    }

    @GetMapping
    public List<EventDto> listOwnEvents(@AuthenticationPrincipal Jwt jwt) {
        return eventService.listOwnEvents(jwt.getSubject());
    }

    @PostMapping
    public ResponseEntity<EventDto> createEvent(
        @AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody CreateEventRequest request
    ) {
        EventDto created = eventService.createEvent(jwt.getSubject(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{eventId}")
    public EventDto getOwnEvent(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID eventId) {
        return eventService.getOwnEventById(eventId, jwt.getSubject());
    }

    @PutMapping("/{eventId}")
    public EventDto updateEvent(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID eventId,
        @Valid @RequestBody UpdateEventRequest request
    ) {
        return eventService.updateEvent(eventId, jwt.getSubject(), request);
    }

    @DeleteMapping("/{eventId}")
    public ResponseEntity<Void> deleteEvent(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID eventId) {
        eventService.deleteEvent(eventId, jwt.getSubject());
        return ResponseEntity.noContent().build();
    }

    /**
     * Mirrors publishEventAction. No generic status-update endpoint exists
     * because the existing application has only this one organizer-side
     * transition — inventing a broader one would not be reproducing
     * existing behavior.
     */
    @PostMapping("/{eventId}/publish")
    public EventDto publishEvent(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID eventId) {
        return eventService.publishEvent(eventId, jwt.getSubject());
    }

    @PostMapping("/{eventId}/ticket-types")
    public ResponseEntity<TicketTypeDto> createTicketType(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID eventId,
        @Valid @RequestBody CreateTicketTypeRequest request
    ) {
        TicketTypeDto created = ticketTypeService.createTicketType(eventId, jwt.getSubject(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @DeleteMapping("/{eventId}/ticket-types/{ticketTypeId}")
    public ResponseEntity<Void> deleteTicketType(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID eventId,
        @PathVariable UUID ticketTypeId
    ) {
        ticketTypeService.deleteTicketType(eventId, ticketTypeId, jwt.getSubject());
        return ResponseEntity.noContent().build();
    }
}
