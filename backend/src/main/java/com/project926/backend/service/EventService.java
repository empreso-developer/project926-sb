package com.project926.backend.service;

import com.project926.backend.dto.CreateEventRequest;
import com.project926.backend.dto.EventDto;
import com.project926.backend.dto.TicketTypeDto;
import com.project926.backend.dto.UpdateEventRequest;
import com.project926.backend.entity.Event;
import com.project926.backend.entity.TicketType;
import com.project926.backend.exception.EventNotFoundException;
import com.project926.backend.exception.ForbiddenException;
import com.project926.backend.repository.EventRepository;
import com.project926.backend.repository.TicketTypeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Mirrors two existing Next.js server-side reads:
 * - app/(project926)/p/page.tsx#getApprovedEvents (listing)
 * - app/(project926)/p/events/[id]/page.tsx#getEvent (detail)
 *
 * Both existing queries are .select('*, ticket_types(*)') with no organizer
 * join, so both are reproduced here as two plain repository calls per
 * event (event + its ticket types) rather than a JPA @OneToMany mapping —
 * simpler and avoids entity-graph complexity not needed for a read-only
 * DTO assembly (see Step 4: avoid unnecessary JPA relationship complexity).
 */
@Service
public class EventService {

    private static final String APPROVED = "approved";

    private final EventRepository eventRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final ProfileService profileService;

    public EventService(
            EventRepository eventRepository,
            TicketTypeRepository ticketTypeRepository,
            ProfileService profileService) {
        this.eventRepository = eventRepository;
        this.ticketTypeRepository = ticketTypeRepository;
        this.profileService = profileService;
    }

    /**
     * Mirrors getApprovedEvents(): status = 'approved', ordered by
     * event_date ascending, no pagination (the existing implementation has
     * none, so none is added here).
     */
    @Transactional(readOnly = true)
    public List<EventDto> listApprovedEvents() {
        return eventRepository.findByStatusOrderByEventDateAsc(APPROVED).stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * Mirrors getEvent(id): looked up by id only, with NO status/approval
     * filter — the existing Next.js event-detail page does not restrict by
     * status either, so this deliberately does not add one. 404 only when
     * the row itself doesn't exist, matching the existing notFound() call.
     */
    @Transactional(readOnly = true)
    public EventDto getEventById(UUID eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(eventId));
        return toDto(event);
    }

    // ---- Organizer-owned write operations (Phase C) --------------------
    // Mirrors lib/actions/events.ts exactly: each of these actions there
    // does its OWN inline auth/ownership check (not a shared helper) —
    // consolidated into one private helper here for the equivalent Java
    // service, but the check itself and its semantics are unchanged:
    // strict organizer_id == caller match, no admin bypass, unlike
    // requireEventOrganizer() in lib/auth/server.ts (used only by
    // check-in/attendees, not by these actions).

    /**
     * Mirrors createEventAction: status is always "draft", organizer_id is
     * always the authenticated caller — neither is ever client-supplied.
     */
    @Transactional
    public EventDto createEvent(String organizerId, CreateEventRequest request) {
        profileService.requireOrganizerOrAdminRole(organizerId);
        Event event = Event.createDraft(
                organizerId,
                request.title(),
                request.description(),
                request.eventDate(),
                request.eventTime(),
                request.venue(),
                request.city(),
                request.bannerUrl());
        eventRepository.save(event);
        return toDto(event);
    }

    /**
     * Mirrors updateEventAction: 'Event not found' vs 'Forbidden' are
     * distinct outcomes there (mapped to 404 vs 403 here — see
     * EventNotFoundException / ForbiddenException). Never touches status.
     */
    @Transactional
    public EventDto updateEvent(UUID eventId, String callerId, UpdateEventRequest request) {
        profileService.requireOrganizerOrAdminRole(callerId);
        Event event = requireOwnedEvent(eventId, callerId);
        event.applyOrganizerUpdate(
                request.title(),
                request.description(),
                request.eventDate(),
                request.eventTime(),
                request.venue(),
                request.city(),
                request.bannerUrl());
        return toDto(event);
    }

    /**
     * Mirrors publishEventAction: unconditionally sets status = 'published'
     * — no check of the current status, matching the existing action
     * exactly (it can "publish" an already-approved or rejected event too).
     */
    @Transactional
    public EventDto publishEvent(UUID eventId, String callerId) {
        profileService.requireOrganizerOrAdminRole(callerId);
        Event event = requireOwnedEvent(eventId, callerId);
        event.setStatus("published");
        return toDto(event);
    }

    /**
     * Mirrors deleteEventAction: a plain delete-by-id. The existing schema
     * cascades this to ticket_types AND bookings/booking_items/payments for
     * this event (ON DELETE CASCADE — see supabase/schema.sql); the
     * existing Next.js action has no guard against this either, so none is
     * added here. See Phase C report for why this is reproduced as-is
     * rather than "fixed".
     */
    @Transactional
    public void deleteEvent(UUID eventId, String callerId) {
        profileService.requireOrganizerOrAdminRole(callerId);
        Event event = requireOwnedEvent(eventId, callerId);
        eventRepository.delete(event);
    }

    /**
     * Mirrors app/(project926)/p/dashboard/organizer/page.tsx's
     * own-events query: organizer_id = caller, ordered by created_at desc.
     */
    @Transactional(readOnly = true)
    public List<EventDto> listOwnEvents(String organizerId) {
        profileService.requireOrganizerOrAdminRole(organizerId);
        return eventRepository.findByOrganizerIdOrderByCreatedAtDesc(organizerId).stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * Mirrors ManageEventPage's own-event lookup exactly, including its
     * specific information-hiding choice: `if (!event) notFound(); if
     * (event.organizer_id !== userId) notFound();` — BOTH cases are 404,
     * not 403, so a non-owner cannot distinguish "doesn't exist" from "not
     * yours" through this endpoint. That is a deliberately different
     * outcome from the write operations below (which use a distinct
     * Forbidden error), because it is what the existing manage-event page
     * itself does.
     */
    @Transactional(readOnly = true)
    public EventDto getOwnEventById(UUID eventId, String callerId) {
        profileService.requireOrganizerOrAdminRole(callerId);
        Event event = eventRepository.findById(eventId)
                .filter(e -> e.getOrganizerId().equals(callerId))
                .orElseThrow(() -> new EventNotFoundException(eventId));
        return toDto(event);
    }

    /**
     * Mirrors lib/auth/server.ts#requireEventOrganizer exactly — reused by
     * the attendee listing and check-in endpoints (Phase F), the same way
     * the original function is shared by the attendees page, scanner page,
     * and check-in route. DISTINCT from requireOwnedEvent above: this one
     * DOES allow a platform admin to act on any event regardless of
     * ownership (`event.organizer_id === userId OR profile?.role ===
     * 'admin'`), matching the original function's own logic — unlike the
     * organizer-CRUD actions in lib/actions/events.ts, which never allow an
     * admin bypass. The two are genuinely different authorization rules in
     * the existing app, not a discrepancy to reconcile.
     *
     * The 'Not authenticated' branch from the original is not reproduced:
     * in this Spring app, reaching this method at all already implies
     * Spring Security accepted a valid Clerk JWT (that authentication
     * check happens centrally, before any controller runs), so that
     * specific outcome is structurally unreachable here — same reasoning
     * already applied throughout Phases B/C.
     */
    @Transactional(readOnly = true)
    public Event requireEventOrganizerOrAdmin(UUID eventId, String callerId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(eventId));
        if (event.getOrganizerId().equals(callerId) || profileService.isAdmin(callerId)) {
            return event;
        }
        throw new ForbiddenException("Forbidden");
    }

    private Event requireOwnedEvent(UUID eventId, String callerId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(eventId));
        if (!event.getOrganizerId().equals(callerId)) {
            throw new ForbiddenException("Not the organizer of this event");
        }
        return event;
    }

    private EventDto toDto(Event event) {
        List<TicketTypeDto> ticketTypes = ticketTypeRepository.findByEventId(event.getId()).stream()
                .map(this::toDto)
                .toList();

        return new EventDto(
                event.getId(),
                event.getOrganizerId(),
                event.getTitle(),
                event.getDescription(),
                event.getEventDate(),
                event.getEventTime(),
                event.getVenue(),
                event.getCity(),
                event.getBannerUrl(),
                event.getStatus(),
                event.getCreatedAt(),
                event.getUpdatedAt(),
                ticketTypes);
    }

    private TicketTypeDto toDto(TicketType t) {
        return new TicketTypeDto(
                t.getId(),
                t.getEventId(),
                t.getName(),
                t.getPrice(),
                t.getQuantityTotal(),
                t.getQuantitySold(),
                t.getSaleStart(),
                t.getSaleEnd(),
                t.getCreatedAt(),
                t.getUpdatedAt());
    }
}
