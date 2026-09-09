package com.project926.backend.service;

import com.project926.backend.dto.CreateEventRequest;
import com.project926.backend.dto.EventDto;
import com.project926.backend.dto.UpdateEventRequest;
import com.project926.backend.entity.Event;
import com.project926.backend.entity.TicketType;
import com.project926.backend.exception.EventNotFoundException;
import com.project926.backend.exception.ForbiddenException;
import com.project926.backend.repository.EventRepository;
import com.project926.backend.repository.TicketTypeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Proves EventService reproduces the two existing Next.js queries at the
 * data-access level, independent of any mocked HTTP layer:
 * - listApprovedEvents(): status='approved', ordered by event_date asc
 * (app/(project926)/p/page.tsx#getApprovedEvents)
 * - getEventById(): looked up by id with NO status filter — any event is
 * readable by id today, same as the existing event-detail page
 * (app/(project926)/p/events/[id]/page.tsx#getEvent)
 */
@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private TicketTypeRepository ticketTypeRepository;

    @Mock
    private ProfileService profileService;

    private EventService service;

    private EventService service() {
        return new EventService(eventRepository, ticketTypeRepository, profileService);
    }

    @Test
    void listApprovedEventsQueriesByApprovedStatusOrderedByEventDateAscending() {
        service = service();
        UUID id = UUID.randomUUID();
        Event approved = event(id, "approved");
        when(eventRepository.findByStatusOrderByEventDateAsc("approved")).thenReturn(List.of(approved));
        when(ticketTypeRepository.findByEventId(id)).thenReturn(List.of());

        List<EventDto> result = service.listApprovedEvents();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).status()).isEqualTo("approved");
        // The exact repository method name encodes the query shape; calling
        // it at all (rather than, say, findAll() + manual filtering) is the
        // proof this issues status='approved' ORDER BY event_date ASC at
        // the database level, matching the existing Supabase query.
        verify(eventRepository).findByStatusOrderByEventDateAsc("approved");
    }

    @Test
    void getEventByIdDoesNotFilterByStatus_anyExistingEventIsReturned() {
        service = service();
        UUID id = UUID.randomUUID();
        // Deliberately a non-approved event, to prove no status check
        // blocks it — this matches the existing getEvent() page query,
        // which has no .eq('status', ...) clause at all.
        Event draftEvent = event(id, "draft");
        when(eventRepository.findById(id)).thenReturn(Optional.of(draftEvent));
        when(ticketTypeRepository.findByEventId(id)).thenReturn(List.of());

        EventDto result = service.getEventById(id);

        assertThat(result.status()).isEqualTo("draft");
        verify(eventRepository).findById(id);
        verifyNoMoreInteractions(eventRepository);
    }

    @Test
    void getEventByIdThrowsNotFoundWhenNoRowExists() {
        service = service();
        UUID id = UUID.randomUUID();
        when(eventRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getEventById(id))
                .isInstanceOf(EventNotFoundException.class);
    }

    @Test
    void eventDtoIncludesNestedTicketTypes() {
        service = service();
        UUID eventId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        Event e = event(eventId, "approved");
        TicketType tt = ticketType(ticketTypeId, eventId);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(e));
        when(ticketTypeRepository.findByEventId(eventId)).thenReturn(List.of(tt));

        EventDto result = service.getEventById(eventId);

        assertThat(result.ticketTypes()).hasSize(1);
        assertThat(result.ticketTypes().get(0).id()).isEqualTo(ticketTypeId);
        assertThat(result.ticketTypes().get(0).quantityTotal()).isEqualTo(100);
        assertThat(result.ticketTypes().get(0).quantitySold()).isEqualTo(10);
    }

    // ---- Phase C: organizer-owned write operations ----------------------

    private CreateEventRequest createRequest() {
        return new CreateEventRequest("Title", "desc", LocalDate.of(2026, 12, 1), LocalTime.of(19, 0), "Venue", "City",
                null);
    }

    private UpdateEventRequest updateRequest() {
        return new UpdateEventRequest("New Title", "new desc", LocalDate.of(2027, 1, 1), LocalTime.of(20, 0),
                "New Venue", "New City", null);
    }

    @Test
    void createEvent_succeedsForOrganizer_andForcesDraftStatusAndCallerAsOrganizer() {
        service = service();
        String organizerId = "user_organizer00000000000";
        when(eventRepository.save(org.mockito.ArgumentMatchers.any(Event.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(ticketTypeRepository.findByEventId(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());

        EventDto result = service.createEvent(organizerId, createRequest());

        assertThat(result.status()).isEqualTo("draft");
        assertThat(result.organizerId()).isEqualTo(organizerId);
        verify(profileService).requireOrganizerOrAdminRole(organizerId);
    }

    @Test
    void createEvent_throwsForbidden_whenCallerIsCustomer() {
        service = service();
        String customerId = "user_customer000000000000";
        doThrow(new ForbiddenException("Requires organizer or admin role"))
                .when(profileService).requireOrganizerOrAdminRole(customerId);

        assertThatThrownBy(() -> service.createEvent(customerId, createRequest()))
                .isInstanceOf(ForbiddenException.class);
        verify(eventRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void updateEvent_succeedsForOwner() {
        service = service();
        UUID id = UUID.randomUUID();
        Event existing = event(id, "draft"); // organizerId = user_organizer00000000000, per the event() fixture
        when(eventRepository.findById(id)).thenReturn(Optional.of(existing));
        when(ticketTypeRepository.findByEventId(id)).thenReturn(List.of());

        EventDto result = service.updateEvent(id, existing.getOrganizerId(), updateRequest());

        assertThat(result.title()).isEqualTo("New Title");
        // Status must be untouched by update — matches updateEventAction
        // never including status in its update payload.
        assertThat(result.status()).isEqualTo("draft");
    }

    @Test
    void updateEvent_throwsForbidden_whenCallerIsNotOwner() {
        service = service();
        UUID id = UUID.randomUUID();
        Event existing = event(id, "draft"); // organizerId = user_organizer00000000000
        when(eventRepository.findById(id)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.updateEvent(id, "user_someoneElse000000000", updateRequest()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void updateEvent_throwsNotFound_whenEventDoesNotExist() {
        service = service();
        UUID id = UUID.randomUUID();
        when(eventRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateEvent(id, "user_owner0000000000000000", updateRequest()))
                .isInstanceOf(EventNotFoundException.class);
    }

    @Test
    void deleteEvent_succeedsForOwner() {
        service = service();
        UUID id = UUID.randomUUID();
        String ownerId = "user_organizer00000000000";
        Event existing = event(id, "draft");
        when(eventRepository.findById(id)).thenReturn(Optional.of(existing));

        service.deleteEvent(id, ownerId);

        verify(eventRepository).delete(existing);
    }

    @Test
    void deleteEvent_throwsForbidden_whenCallerIsNotOwner() {
        service = service();
        UUID id = UUID.randomUUID();
        Event existing = event(id, "draft");
        when(eventRepository.findById(id)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.deleteEvent(id, "user_someoneElse000000000"))
                .isInstanceOf(ForbiddenException.class);
        verify(eventRepository, never()).delete(org.mockito.ArgumentMatchers.any(Event.class));
    }

    @Test
    void publishEvent_setsStatusUnconditionally_regardlessOfCurrentStatus() {
        service = service();
        UUID id = UUID.randomUUID();
        String ownerId = "user_organizer00000000000";
        // Deliberately starting from "rejected" — the existing
        // publishEventAction has no current-status guard, so this must
        // still succeed, matching that exact permissiveness.
        Event rejectedEvent = event(id, "rejected");
        when(eventRepository.findById(id)).thenReturn(Optional.of(rejectedEvent));
        when(ticketTypeRepository.findByEventId(id)).thenReturn(List.of());

        EventDto result = service.publishEvent(id, ownerId);

        assertThat(result.status()).isEqualTo("published");
    }

    @Test
    void getOwnEventById_returns404_notForbidden_whenCallerIsNotOwner() {
        // Reproduces ManageEventPage's information-hiding choice exactly:
        // both "doesn't exist" and "not yours" are 404 here, unlike the
        // write operations above which distinguish 404 vs 403.
        service = service();
        UUID id = UUID.randomUUID();
        Event existing = event(id, "draft"); // organizerId = user_organizer00000000000
        when(eventRepository.findById(id)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.getOwnEventById(id, "user_someoneElse000000000"))
                .isInstanceOf(EventNotFoundException.class);
    }

    private Event event(UUID id, String status) {
        Event e = newInstance(Event.class);
        set(e, "id", id);
        set(e, "organizerId", "user_organizer00000000000");
        set(e, "title", "Test Event");
        set(e, "description", "desc");
        set(e, "eventDate", LocalDate.of(2026, 12, 1));
        set(e, "eventTime", LocalTime.of(19, 0));
        set(e, "venue", "Venue");
        set(e, "city", "City");
        set(e, "bannerUrl", null);
        set(e, "status", status);
        set(e, "createdAt", OffsetDateTime.now());
        set(e, "updatedAt", OffsetDateTime.now());
        return e;
    }

    private TicketType ticketType(UUID id, UUID eventId) {
        TicketType t = newInstance(TicketType.class);
        set(t, "id", id);
        set(t, "eventId", eventId);
        set(t, "name", "General");
        set(t, "price", new BigDecimal("500.00"));
        set(t, "quantityTotal", 100);
        set(t, "quantitySold", 10);
        set(t, "saleStart", null);
        set(t, "saleEnd", null);
        set(t, "createdAt", OffsetDateTime.now());
        set(t, "updatedAt", OffsetDateTime.now());
        return t;
    }

    /**
     * Entities have no setters and a protected no-arg constructor by design
     * (JPA-managed); reflection is only for building fixtures in this test.
     */
    private static void set(Object target, String field, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(field);
            f.setAccessible(true);
            f.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static <T> T newInstance(Class<T> type) {
        try {
            var constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
