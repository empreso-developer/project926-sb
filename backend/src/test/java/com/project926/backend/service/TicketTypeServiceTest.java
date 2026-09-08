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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Mirrors createTicketTypeAction / deleteTicketTypeAction in
 * lib/actions/events.ts, including their ownership-via-parent-event checks.
 */
@ExtendWith(MockitoExtension.class)
class TicketTypeServiceTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private TicketTypeRepository ticketTypeRepository;

    @Mock
    private ProfileService profileService;

    private TicketTypeService service() {
        return new TicketTypeService(eventRepository, ticketTypeRepository, profileService);
    }

    private CreateTicketTypeRequest request() {
        return new CreateTicketTypeRequest("General", new BigDecimal("500.00"), 100, null, null);
    }

    private Event event(UUID id, String organizerId) {
        Event e = newInstance(Event.class);
        set(e, "id", id);
        set(e, "organizerId", organizerId);
        set(e, "title", "Test Event");
        set(e, "status", "draft");
        set(e, "createdAt", OffsetDateTime.now());
        set(e, "updatedAt", OffsetDateTime.now());
        return e;
    }

    @Test
    void createTicketType_succeedsForEventOwner() {
        UUID eventId = UUID.randomUUID();
        String ownerId = "user_owner0000000000000000";
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event(eventId, ownerId)));
        when(ticketTypeRepository.save(any(TicketType.class))).thenAnswer(inv -> inv.getArgument(0));

        TicketTypeDto result = service().createTicketType(eventId, ownerId, request());

        assertThat(result.eventId()).isEqualTo(eventId);
        assertThat(result.quantitySold()).isEqualTo(0);
        verify(profileService).requireOrganizerOrAdminRole(ownerId);
    }

    @Test
    void createTicketType_throwsForbidden_whenCallerDoesNotOwnEvent() {
        UUID eventId = UUID.randomUUID();
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event(eventId, "user_owner0000000000000000")));

        assertThatThrownBy(() -> service().createTicketType(eventId, "user_someoneElse000000000", request()))
            .isInstanceOf(ForbiddenException.class);
        verify(ticketTypeRepository, never()).save(any());
    }

    @Test
    void createTicketType_throwsNotFound_whenEventDoesNotExist() {
        UUID eventId = UUID.randomUUID();
        when(eventRepository.findById(eventId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().createTicketType(eventId, "user_owner0000000000000000", request()))
            .isInstanceOf(EventNotFoundException.class);
    }

    @Test
    void deleteTicketType_succeedsForEventOwner() {
        UUID eventId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        String ownerId = "user_owner0000000000000000";
        TicketType tt = newInstance(TicketType.class);
        set(tt, "id", ticketTypeId);
        set(tt, "eventId", eventId);
        when(ticketTypeRepository.findById(ticketTypeId)).thenReturn(Optional.of(tt));
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event(eventId, ownerId)));

        service().deleteTicketType(eventId, ticketTypeId, ownerId);

        verify(ticketTypeRepository).delete(tt);
    }

    @Test
    void deleteTicketType_throwsForbidden_whenCallerDoesNotOwnParentEvent() {
        UUID eventId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        TicketType tt = newInstance(TicketType.class);
        set(tt, "id", ticketTypeId);
        set(tt, "eventId", eventId);
        when(ticketTypeRepository.findById(ticketTypeId)).thenReturn(Optional.of(tt));
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event(eventId, "user_owner0000000000000000")));

        assertThatThrownBy(() -> service().deleteTicketType(eventId, ticketTypeId, "user_someoneElse000000000"))
            .isInstanceOf(ForbiddenException.class);
        verify(ticketTypeRepository, never()).delete(any(TicketType.class));
    }

    @Test
    void deleteTicketType_throwsNotFound_whenTicketTypeDoesNotExist() {
        UUID eventId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        when(ticketTypeRepository.findById(ticketTypeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().deleteTicketType(eventId, ticketTypeId, "user_owner0000000000000000"))
            .isInstanceOf(TicketTypeNotFoundException.class);
    }

    @Test
    void deleteTicketType_throwsNotFound_whenTicketTypeBelongsToDifferentEvent() {
        UUID actualEventId = UUID.randomUUID();
        UUID otherEventIdInUrl = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        TicketType tt = newInstance(TicketType.class);
        set(tt, "id", ticketTypeId);
        set(tt, "eventId", actualEventId);
        when(ticketTypeRepository.findById(ticketTypeId)).thenReturn(Optional.of(tt));

        assertThatThrownBy(() -> service().deleteTicketType(otherEventIdInUrl, ticketTypeId, "user_owner0000000000000000"))
            .isInstanceOf(TicketTypeNotFoundException.class);
    }

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
