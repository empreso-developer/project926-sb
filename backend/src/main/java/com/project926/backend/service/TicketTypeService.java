package com.project926.backend.service;

import com.project926.backend.dto.CreateTicketTypeRequest;
import com.project926.backend.dto.TicketTypeDto;
import com.project926.backend.entity.Event;
import com.project926.backend.entity.TicketType;
import com.project926.backend.exception.EventNotFoundException;
import com.project926.backend.exception.ForbiddenException;
import com.project926.backend.exception.TicketTypeNotFoundException;
import com.project926.backend.repository.EventRepository;
import com.project926.backend.repository.TicketTypeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Mirrors createTicketTypeAction / deleteTicketTypeAction in
 * lib/actions/events.ts exactly, including their ownership checks (strict
 * organizer_id == caller, no admin bypass — same reasoning as EventService).
 */
@Service
public class TicketTypeService {

    private final EventRepository eventRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final ProfileService profileService;

    public TicketTypeService(
        EventRepository eventRepository,
        TicketTypeRepository ticketTypeRepository,
        ProfileService profileService
    ) {
        this.eventRepository = eventRepository;
        this.ticketTypeRepository = ticketTypeRepository;
        this.profileService = profileService;
    }

    /**
     * Mirrors createTicketTypeAction: ownership is checked against the
     * *parent event*, not the (not-yet-existing) ticket type. quantity_sold
     * is never set — see TicketType.create().
     */
    @Transactional
    public TicketTypeDto createTicketType(UUID eventId, String callerId, CreateTicketTypeRequest request) {
        profileService.requireOrganizerOrAdminRole(callerId);
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new EventNotFoundException(eventId));
        if (!event.getOrganizerId().equals(callerId)) {
            throw new ForbiddenException("Not the organizer of this event");
        }

        TicketType ticketType = TicketType.create(
            eventId,
            request.name(),
            request.price(),
            request.quantityTotal(),
            request.saleStart(),
            request.saleEnd()
        );
        ticketTypeRepository.save(ticketType);
        return toDto(ticketType);
    }

    /**
     * Mirrors deleteTicketTypeAction: ownership is resolved through the
     * ticket type's parent event (same as the existing
     * `.select('event_id, events(organizer_id)')` join). 'Ticket type not
     * found' vs 'Forbidden' are distinct there, mapped to 404 vs 403 here.
     *
     * Does NOT guard against the ticket type having booking_items — the
     * existing action doesn't either; the database's own
     * booking_items.ticket_type_id ON DELETE RESTRICT constraint is what
     * actually blocks this (surfaces as a 409 via GlobalExceptionHandler).
     */
    @Transactional
    public void deleteTicketType(UUID eventId, UUID ticketTypeId, String callerId) {
        profileService.requireOrganizerOrAdminRole(callerId);
        TicketType ticketType = ticketTypeRepository.findById(ticketTypeId)
            .filter(t -> t.getEventId().equals(eventId))
            .orElseThrow(() -> new TicketTypeNotFoundException(ticketTypeId));

        Event event = eventRepository.findById(ticketType.getEventId())
            .orElseThrow(() -> new TicketTypeNotFoundException(ticketTypeId));
        if (!event.getOrganizerId().equals(callerId)) {
            throw new ForbiddenException("Not the organizer of this event");
        }

        ticketTypeRepository.delete(ticketType);
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
            t.getUpdatedAt()
        );
    }
}
