package com.project926.backend.service;

import com.project926.backend.dto.ClerkEmailAddress;
import com.project926.backend.dto.ClerkUserData;
import com.project926.backend.repository.ProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Mocked-repository coverage of ClerkWebhookService's actual profile
 * behavior — mirrors the exact field-by-field audit of
 * app/(project926)/p/api/webhooks/clerk/route.ts this was built from.
 * Real DB behavior (the atomic upsert/delete queries themselves) is
 * proven separately by ClerkWebhookIT against the dev DB.
 */
@ExtendWith(MockitoExtension.class)
class ClerkWebhookServiceTest {

    @Mock private ProfileRepository profileRepository;

    private ClerkWebhookService service() {
        return new ClerkWebhookService(profileRepository);
    }

    private ClerkUserData data(String id, List<ClerkEmailAddress> emails, String primaryEmailId,
                                String firstName, String lastName,
                                Map<String, Object> publicMetadata, Map<String, Object> unsafeMetadata) {
        return new ClerkUserData(id, emails, primaryEmailId, firstName, lastName, publicMetadata, unsafeMetadata);
    }

    // ---- user.created / user.updated: email selection ----

    @Test
    void userCreated_selectsEmailMatchingPrimaryEmailAddressId() {
        List<ClerkEmailAddress> emails = List.of(
            new ClerkEmailAddress("email_1", "secondary@example.com"),
            new ClerkEmailAddress("email_2", "primary@example.com"));
        ClerkUserData d = data("user_x", emails, "email_2", "First", "Last", Map.of(), Map.of());

        service().process("user.created", d);

        verify(profileRepository).upsertFromClerk("user_x", "primary@example.com", "First", "Last", "customer");
    }

    @Test
    void userCreated_noMatchingPrimaryId_fallsBackToFirstEmail() {
        List<ClerkEmailAddress> emails = List.of(
            new ClerkEmailAddress("email_1", "first@example.com"),
            new ClerkEmailAddress("email_2", "second@example.com"));
        ClerkUserData d = data("user_x", emails, "email_nonexistent", "First", "Last", Map.of(), Map.of());

        service().process("user.created", d);

        verify(profileRepository).upsertFromClerk("user_x", "first@example.com", "First", "Last", "customer");
    }

    @Test
    void userCreated_noEmailsAtAll_usesEmptyString() {
        ClerkUserData d = data("user_x", List.of(), null, "First", "Last", Map.of(), Map.of());

        service().process("user.created", d);

        verify(profileRepository).upsertFromClerk("user_x", "", "First", "Last", "customer");
    }

    // ---- role resolution ----

    @Test
    void userCreated_noMetadataRole_defaultsToCustomer() {
        ClerkUserData d = data("user_x", List.of(), null, null, null, Map.of(), Map.of());

        service().process("user.created", d);

        verify(profileRepository).upsertFromClerk(anyString(), anyString(), any(), any(), org.mockito.ArgumentMatchers.eq("customer"));
    }

    @Test
    void userCreated_unsafeMetadataRole_takesPrecedenceOverPublicMetadata() {
        ClerkUserData d = data("user_x", List.of(), null, null, null,
            Map.of("role", "admin"), Map.of("role", "organizer"));

        service().process("user.created", d);

        verify(profileRepository).upsertFromClerk(anyString(), anyString(), any(), any(), org.mockito.ArgumentMatchers.eq("organizer"));
    }

    @Test
    void userCreated_publicMetadataRole_usedWhenUnsafeMetadataAbsent() {
        ClerkUserData d = data("user_x", List.of(), null, null, null,
            Map.of("role", "admin"), Map.of());

        service().process("user.created", d);

        verify(profileRepository).upsertFromClerk(anyString(), anyString(), any(), any(), org.mockito.ArgumentMatchers.eq("admin"));
    }

    @Test
    void userCreated_invalidRoleValue_defaultsToCustomer() {
        ClerkUserData d = data("user_x", List.of(), null, null, null,
            Map.of(), Map.of("role", "superadmin"));

        service().process("user.created", d);

        verify(profileRepository).upsertFromClerk(anyString(), anyString(), any(), any(), org.mockito.ArgumentMatchers.eq("customer"));
    }

    @Test
    void userCreated_blankRoleValue_treatedAsAbsent_defaultsToCustomer() {
        ClerkUserData d = data("user_x", List.of(), null, null, null,
            Map.of(), Map.of("role", "   "));

        service().process("user.created", d);

        verify(profileRepository).upsertFromClerk(anyString(), anyString(), any(), any(), org.mockito.ArgumentMatchers.eq("customer"));
    }

    // ---- user.updated behaves identically to user.created ----

    @Test
    void userUpdated_sameUpsertBehaviorAsCreated() {
        ClerkUserData d = data("user_x", List.of(new ClerkEmailAddress("e1", "a@example.com")), "e1",
            "New", "Name", Map.of(), Map.of("role", "organizer"));

        service().process("user.updated", d);

        verify(profileRepository).upsertFromClerk("user_x", "a@example.com", "New", "Name", "organizer");
    }

    // ---- duplicate delivery safety (idempotency) ----

    @Test
    void duplicateUserCreatedDelivery_callsUpsertTwice_bothConverge() {
        ClerkUserData d = data("user_x", List.of(), null, "First", "Last", Map.of(), Map.of());

        service().process("user.created", d);
        service().process("user.created", d);

        verify(profileRepository, org.mockito.Mockito.times(2))
            .upsertFromClerk("user_x", "", "First", "Last", "customer");
    }

    // ---- user.deleted ----

    @Test
    void userDeleted_deletesById() {
        ClerkUserData d = data("user_x", List.of(), null, null, null, Map.of(), Map.of());
        when(profileRepository.deleteByIdSafe("user_x")).thenReturn(1);

        service().process("user.deleted", d);

        verify(profileRepository).deleteByIdSafe("user_x");
        verify(profileRepository, never()).upsertFromClerk(any(), any(), any(), any(), any());
    }

    @Test
    void duplicateUserDeletedDelivery_secondCallSafeZeroRows() {
        ClerkUserData d = data("user_x", List.of(), null, null, null, Map.of(), Map.of());
        when(profileRepository.deleteByIdSafe("user_x")).thenReturn(1, 0);

        service().process("user.deleted", d);
        service().process("user.deleted", d);

        verify(profileRepository, org.mockito.Mockito.times(2)).deleteByIdSafe("user_x");
    }

    // ---- unsupported event types ----

    @Test
    void unsupportedEventType_isIgnored_noRepositoryCalls() {
        ClerkUserData d = data("user_x", List.of(), null, null, null, Map.of(), Map.of());

        service().process("session.created", d);

        verify(profileRepository, never()).upsertFromClerk(any(), any(), any(), any(), any());
        verify(profileRepository, never()).deleteByIdSafe(any());
    }
}
