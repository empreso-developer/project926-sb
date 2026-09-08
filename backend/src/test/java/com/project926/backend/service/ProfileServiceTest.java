package com.project926.backend.service;

import com.project926.backend.entity.Profile;
import com.project926.backend.repository.ProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Mirrors app/(project926)/project926/api/profile/route.ts at the
 * data-access level: role lookup by Clerk id, defaulting to "customer"
 * when no profile row exists — no write, no exception.
 */
@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    @Mock
    private ProfileRepository profileRepository;

    @Test
    void returnsStoredRoleWhenProfileExists() {
        String clerkUserId = "user_hasProfile00000000000";
        Profile profile = profile(clerkUserId, "organizer");
        when(profileRepository.findById(clerkUserId)).thenReturn(Optional.of(profile));

        ProfileService service = new ProfileService(profileRepository);
        var response = service.getRoleForAuthenticatedUser(clerkUserId);

        assertThat(response.role()).isEqualTo("organizer");
    }

    @Test
    void defaultsToCustomerWhenNoProfileRowExists() {
        String clerkUserId = "user_noProfileYet00000000";
        when(profileRepository.findById(clerkUserId)).thenReturn(Optional.empty());

        ProfileService service = new ProfileService(profileRepository);
        var response = service.getRoleForAuthenticatedUser(clerkUserId);

        assertThat(response.role()).isEqualTo("customer");
    }

    @Test
    void clerkUserIdStaysAString() {
        String clerkUserId = "user_3GjubwvONQwJQcpQRdNtbEUEbP5";
        assertThat(clerkUserId).isInstanceOf(String.class);
        // Documents intent: ProfileRepository is keyed by String (see
        // ProfileRepository<Profile, String>), so this call would not even
        // compile if Clerk ids were ever changed to UUID.
        when(profileRepository.findById(clerkUserId)).thenReturn(Optional.empty());
        new ProfileService(profileRepository).getRoleForAuthenticatedUser(clerkUserId);
    }

    private Profile profile(String id, String role) {
        Profile p = newInstance(Profile.class);
        set(p, "id", id);
        set(p, "email", "test@example.com");
        set(p, "firstName", "Test");
        set(p, "lastName", "User");
        set(p, "role", role);
        return p;
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
