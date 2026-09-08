package com.project926.backend.service;

import com.project926.backend.dto.AdminEventDto;
import com.project926.backend.entity.Event;
import com.project926.backend.entity.Profile;
import com.project926.backend.exception.EventNotFoundException;
import com.project926.backend.exception.ForbiddenException;
import com.project926.backend.repository.EventRepository;
import com.project926.backend.repository.ProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Mirrors lib/actions/admin.ts's requireAdmin()-gated event actions.
 * Critically: NO ownership check exists in the original — an admin can act
 * on ANY event, so these tests deliberately use an event whose organizer is
 * a different user than the caller to prove that.
 */
@ExtendWith(MockitoExtension.class)
class EventModerationServiceTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private ProfileRepository profileRepository;

    private EventModerationService service() {
        return new EventModerationService(eventRepository, profileRepository);
    }

    private Profile profile(String id, String role) {
        Profile p = newInstance(Profile.class);
        set(p, "id", id);
        set(p, "email", id + "@example.com");
        set(p, "role", role);
        return p;
    }

    private Event event(UUID id, String status, String organizerId) {
        Event e = newInstance(Event.class);
        set(e, "id", id);
        set(e, "organizerId", organizerId);
        set(e, "title", "Some Event");
        set(e, "status", status);
        set(e, "createdAt", OffsetDateTime.now());
        set(e, "updatedAt", OffsetDateTime.now());
        return e;
    }

    @Test
    void approveEvent_succeedsForAdmin_evenWhenAdminDoesNotOwnTheEvent() {
        UUID eventId = UUID.randomUUID();
        String adminId = "user_admin0000000000000000";
        Event notOwnedByAdmin = event(eventId, "published", "user_someOtherOrganizer000");
        when(profileRepository.findById(adminId)).thenReturn(Optional.of(profile(adminId, "admin")));
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(notOwnedByAdmin));

        service().approveEvent(eventId, adminId);

        assertThat(notOwnedByAdmin.getStatus()).isEqualTo("approved");
    }

    @Test
    void approveEvent_throwsForbidden_whenCallerIsNotAdmin() {
        UUID eventId = UUID.randomUUID();
        String organizerId = "user_organizer00000000000";
        when(profileRepository.findById(organizerId)).thenReturn(Optional.of(profile(organizerId, "organizer")));

        assertThatThrownBy(() -> service().approveEvent(eventId, organizerId))
            .isInstanceOf(ForbiddenException.class);
        verify(eventRepository, never()).findById(any());
    }

    @Test
    void approveEvent_worksEvenFromRejectedStatus_noStateMachineGuard() {
        // Proves no current-status guard exists, matching the existing
        // unconditional `.update({ status: 'approved' })`.
        UUID eventId = UUID.randomUUID();
        String adminId = "user_admin0000000000000000";
        Event rejected = event(eventId, "rejected", "user_someOrganizer00000000");
        when(profileRepository.findById(adminId)).thenReturn(Optional.of(profile(adminId, "admin")));
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(rejected));

        service().approveEvent(eventId, adminId);

        assertThat(rejected.getStatus()).isEqualTo("approved");
    }

    @Test
    void rejectEvent_succeedsForAdmin() {
        UUID eventId = UUID.randomUUID();
        String adminId = "user_admin0000000000000000";
        Event e = event(eventId, "published", "user_someOrganizer00000000");
        when(profileRepository.findById(adminId)).thenReturn(Optional.of(profile(adminId, "admin")));
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(e));

        service().rejectEvent(eventId, adminId);

        assertThat(e.getStatus()).isEqualTo("rejected");
    }

    @Test
    void rejectEvent_throwsForbidden_whenCallerIsNotAdmin() {
        UUID eventId = UUID.randomUUID();
        String customerId = "user_customer000000000000";
        when(profileRepository.findById(customerId)).thenReturn(Optional.empty()); // defaults to "customer"

        assertThatThrownBy(() -> service().rejectEvent(eventId, customerId))
            .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void removeEvent_succeedsForAdmin_regardlessOfEventOwnership() {
        UUID eventId = UUID.randomUUID();
        String adminId = "user_admin0000000000000000";
        Event notOwnedByAdmin = event(eventId, "approved", "user_someOtherOrganizer000");
        when(profileRepository.findById(adminId)).thenReturn(Optional.of(profile(adminId, "admin")));
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(notOwnedByAdmin));

        service().removeEvent(eventId, adminId);

        verify(eventRepository).delete(notOwnedByAdmin);
    }

    @Test
    void removeEvent_throwsNotFound_whenEventDoesNotExist() {
        UUID eventId = UUID.randomUUID();
        String adminId = "user_admin0000000000000000";
        when(profileRepository.findById(adminId)).thenReturn(Optional.of(profile(adminId, "admin")));
        when(eventRepository.findById(eventId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().removeEvent(eventId, adminId))
            .isInstanceOf(EventNotFoundException.class);
    }

    @Test
    void listAllEventsForAdmin_includesOrganizerInfo_andRequiresAdmin() {
        String adminId = "user_admin0000000000000000";
        String organizerId = "user_organizer00000000000";
        UUID eventId = UUID.randomUUID();
        when(profileRepository.findById(adminId)).thenReturn(Optional.of(profile(adminId, "admin")));
        when(eventRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(event(eventId, "published", organizerId)));
        when(profileRepository.findAllById(List.of(organizerId))).thenReturn(List.of(profile(organizerId, "organizer")));

        List<AdminEventDto> result = service().listAllEventsForAdmin(adminId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).organizerId()).isEqualTo(organizerId);
        assertThat(result.get(0).organizerEmail()).isEqualTo(organizerId + "@example.com");
    }

    @Test
    void listAllEventsForAdmin_throwsForbidden_whenCallerIsNotAdmin() {
        String organizerId = "user_organizer00000000000";
        when(profileRepository.findById(organizerId)).thenReturn(Optional.of(profile(organizerId, "organizer")));

        assertThatThrownBy(() -> service().listAllEventsForAdmin(organizerId))
            .isInstanceOf(ForbiddenException.class);
        verify(eventRepository, never()).findAllByOrderByCreatedAtDesc();
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
