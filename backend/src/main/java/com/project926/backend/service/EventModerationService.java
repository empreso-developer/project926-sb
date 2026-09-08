package com.project926.backend.service;

import com.project926.backend.dto.AdminEventDto;
import com.project926.backend.entity.Event;
import com.project926.backend.entity.Profile;
import com.project926.backend.exception.EventNotFoundException;
import com.project926.backend.exception.ForbiddenException;
import com.project926.backend.repository.EventRepository;
import com.project926.backend.repository.ProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Mirrors lib/actions/admin.ts's requireAdmin()-gated actions
 * (approveEventAction / rejectEventAction / removeEventAction) and the
 * admin dashboard's all-events read. Unlike EventService's organizer
 * operations, these have NO ownership check at all — any admin may
 * approve/reject/remove ANY event regardless of who owns it, exactly
 * matching the existing requireAdmin() + unconditional update/delete
 * pattern in lib/actions/admin.ts.
 */
@Service
public class EventModerationService {

    private final EventRepository eventRepository;
    private final ProfileRepository profileRepository;

    public EventModerationService(EventRepository eventRepository, ProfileRepository profileRepository) {
        this.eventRepository = eventRepository;
        this.profileRepository = profileRepository;
    }

    /**
     * Mirrors requireAdmin(): fetch the caller's profile, require role ==
     * 'admin'. A missing profile fails this the same way it does in the
     * existing code (`!profile || profile.role !== 'admin'`), since
     * ProfileRepository.findById returning empty is treated as not-admin
     * below.
     */
    private void requireAdmin(String callerId) {
        boolean isAdmin = profileRepository.findById(callerId)
            .map(p -> "admin".equals(p.getRole()))
            .orElse(false);
        if (!isAdmin) {
            throw new ForbiddenException("Requires admin role");
        }
    }

    /** Mirrors approveEventAction: unconditional status = 'approved', no current-status check, no ownership check. */
    @Transactional
    public void approveEvent(UUID eventId, String callerId) {
        requireAdmin(callerId);
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new EventNotFoundException(eventId));
        event.setStatus("approved");
    }

    /** Mirrors rejectEventAction: unconditional status = 'rejected'. */
    @Transactional
    public void rejectEvent(UUID eventId, String callerId) {
        requireAdmin(callerId);
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new EventNotFoundException(eventId));
        event.setStatus("rejected");
    }

    /**
     * Mirrors removeEventAction: a plain delete-by-id, no ownership check.
     * Same cascade caveat as EventService.deleteEvent — this also cascades
     * to ticket_types/bookings/booking_items/payments for this event via
     * the existing FK constraints, exactly as the existing action does.
     */
    @Transactional
    public void removeEvent(UUID eventId, String callerId) {
        requireAdmin(callerId);
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new EventNotFoundException(eventId));
        eventRepository.delete(event);
    }

    /**
     * Mirrors the admin dashboard's all-events query:
     * {@code .select('*, organizer:profiles!events_organizer_id_fkey(*)').order('created_at', {ascending:false})}
     * — batch-loads organizer profiles rather than a JPA join, same
     * "avoid unnecessary relationship complexity" reasoning as EventService.
     */
    @Transactional(readOnly = true)
    public List<AdminEventDto> listAllEventsForAdmin(String callerId) {
        requireAdmin(callerId);

        List<Event> events = eventRepository.findAllByOrderByCreatedAtDesc();
        List<String> organizerIds = events.stream().map(Event::getOrganizerId).distinct().toList();
        Map<String, Profile> profilesById = profileRepository.findAllById(organizerIds).stream()
            .collect(Collectors.toMap(Profile::getId, p -> p));

        return events.stream()
            .map(event -> toAdminDto(event, profilesById.get(event.getOrganizerId())))
            .toList();
    }

    private AdminEventDto toAdminDto(Event event, Profile organizer) {
        return new AdminEventDto(
            event.getId(),
            event.getOrganizerId(),
            organizer != null ? organizer.getEmail() : null,
            organizer != null ? organizer.getFirstName() : null,
            organizer != null ? organizer.getLastName() : null,
            event.getTitle(),
            event.getDescription(),
            event.getEventDate(),
            event.getEventTime(),
            event.getVenue(),
            event.getCity(),
            event.getBannerUrl(),
            event.getStatus(),
            event.getCreatedAt(),
            event.getUpdatedAt()
        );
    }
}
