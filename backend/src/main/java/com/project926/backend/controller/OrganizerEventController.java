package com.project926.backend.controller;

import com.project926.backend.dto.AttendeeListResponse;
import com.project926.backend.dto.CheckInRequest;
import com.project926.backend.dto.CheckInResponse;
import com.project926.backend.dto.CreateEventRequest;
import com.project926.backend.dto.CreateTicketTypeRequest;
import com.project926.backend.dto.EventDto;
import com.project926.backend.dto.EventScanDto;
import com.project926.backend.dto.TicketTypeDto;
import com.project926.backend.dto.UpdateEventRequest;
import com.project926.backend.entity.Event;
import com.project926.backend.exception.EventNotFoundException;
import com.project926.backend.exception.ForbiddenException;
import com.project926.backend.service.AttendeeService;
import com.project926.backend.service.CheckInService;
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
import org.springframework.web.bind.annotation.RequestParam;
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
    private final AttendeeService attendeeService;
    private final CheckInService checkInService;

    public OrganizerEventController(
        EventService eventService,
        TicketTypeService ticketTypeService,
        AttendeeService attendeeService,
        CheckInService checkInService
    ) {
        this.eventService = eventService;
        this.ticketTypeService = ticketTypeService;
        this.attendeeService = attendeeService;
        this.checkInService = checkInService;
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

    /**
     * Mirrors AttendeesPage's data (Phase F). Authorization is
     * requireEventOrganizerOrAdmin — DISTINCT from every other endpoint in
     * this controller (which use profileService.requireOrganizerOrAdminRole
     * + strict ownership, no admin bypass): this one allows any platform
     * admin, not just this event's own organizer, matching the existing
     * shared requireEventOrganizer() function exactly (see EventService).
     */
    @GetMapping("/{eventId}/attendees")
    public AttendeeListResponse listAttendees(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID eventId,
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) String filter,
        @RequestParam(name = "q", required = false) String search
    ) {
        return attendeeService.listAttendees(eventId, jwt.getSubject(), page, filter, search);
    }

    /**
     * Mirrors lib/auth/server.ts#requireEventOrganizer's two responsibilities
     * for the organizer scan page (see scan/page.tsx): authorization
     * (owner-or-admin, via the shared requireEventOrganizerOrAdmin — same
     * check the attendees/check-in endpoints above and below already use,
     * not duplicated here) and the minimal event info the page displays.
     * A plain 404/403 (via EventNotFoundException/ForbiddenException,
     * mapped by GlobalExceptionHandler) is intentional here — unlike
     * checkIn() below, this endpoint has no special response-shape
     * requirement, so there is no reason to catch and re-wrap those.
     */
    @GetMapping("/{eventId}/scan")
    public EventScanDto getEventForScan(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID eventId) {
        Event event = eventService.requireEventOrganizerOrAdmin(eventId, jwt.getSubject());
        return new EventScanDto(event.getId(), event.getTitle());
    }

    /**
     * Mirrors the existing check-in route's structure deliberately closely,
     * INCLUDING doing its own inline authorization try/catch here rather
     * than in the service (see CheckInService's Javadoc for why): the
     * existing route computes its HTTP status per auth-failure cause
     * (401/404/403) but always uses the SAME body shape
     * ({@code {success:false,status:'unauthorized',error:message}}) — a
     * response-shape/status-code coupling that belongs at this layer, not
     * buried in a generic exception handler shared by every other endpoint.
     *
     * Body validation (400 "invalid" when neither bookingId nor a
     * non-blank reference is supplied) mirrors the existing zod
     * {@code .refine()} check, done here rather than via {@code @Valid}
     * for the same reason — the existing route's 400 body is
     * {@code {success:false,status:'invalid'}}, not a generic validation-
     * error shape.
     */
    @PostMapping("/{eventId}/check-in")
    public ResponseEntity<CheckInResponse> checkIn(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID eventId,
        @RequestBody CheckInRequest request
    ) {
        try {
            eventService.requireEventOrganizerOrAdmin(eventId, jwt.getSubject());
        } catch (EventNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(CheckInResponse.unauthorized("Event not found"));
        } catch (ForbiddenException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(CheckInResponse.unauthorized("Forbidden"));
        }

        boolean hasBookingId = request.bookingId() != null;
        boolean hasReference = request.reference() != null && !request.reference().isBlank();
        if (!hasBookingId && !hasReference) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(CheckInResponse.status("invalid"));
        }

        CheckInResponse response = checkInService.checkIn(eventId, jwt.getSubject(), request.bookingId(), request.reference());
        return ResponseEntity.ok(response);
    }
}
