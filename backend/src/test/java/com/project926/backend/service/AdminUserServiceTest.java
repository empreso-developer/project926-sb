package com.project926.backend.service;

import com.project926.backend.dto.AdminRevenueDto;
import com.project926.backend.dto.AdminUserDto;
import com.project926.backend.entity.Profile;
import com.project926.backend.exception.ForbiddenException;
import com.project926.backend.exception.ProfileNotFoundException;
import com.project926.backend.repository.BookingRepository;
import com.project926.backend.repository.ProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Mocked-repository coverage of AdminUserService's mapping/authorization
 * logic. Real query behavior against actual rows is proven separately by
 * AdminUserManagementIT against the dev DB.
 */
@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock private ProfileRepository profileRepository;
    @Mock private BookingRepository bookingRepository;

    private AdminUserService service() {
        return new AdminUserService(profileRepository, bookingRepository);
    }

    private static final String ADMIN_ID = "user_admin0000000000000000";

    private Profile profile(String id, String role) {
        Profile p = newInstance(Profile.class);
        set(p, "id", id);
        set(p, "email", id + "@example.com");
        set(p, "firstName", "First");
        set(p, "lastName", "Last");
        set(p, "role", role);
        set(p, "createdAt", OffsetDateTime.now());
        set(p, "updatedAt", OffsetDateTime.now());
        return p;
    }

    // ---- listAllUsers ----

    @Test
    void listAllUsers_asAdmin_returnsAllProfiles() {
        when(profileRepository.findById(ADMIN_ID)).thenReturn(Optional.of(profile(ADMIN_ID, "admin")));
        Profile customer = profile("user_customer000000000000", "customer");
        when(profileRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(customer));

        List<AdminUserDto> result = service().listAllUsers(ADMIN_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(customer.getId());
        assertThat(result.get(0).email()).isEqualTo(customer.getEmail());
        assertThat(result.get(0).role()).isEqualTo("customer");
    }

    @Test
    void listAllUsers_zeroUsers_returnsEmptyList() {
        when(profileRepository.findById(ADMIN_ID)).thenReturn(Optional.of(profile(ADMIN_ID, "admin")));
        when(profileRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of());

        assertThat(service().listAllUsers(ADMIN_ID)).isEmpty();
    }

    @Test
    void listAllUsers_nonAdminCaller_throwsForbidden_andNeverQueries() {
        String customerId = "user_customer000000000000";
        when(profileRepository.findById(customerId)).thenReturn(Optional.of(profile(customerId, "customer")));

        assertThatThrownBy(() -> service().listAllUsers(customerId)).isInstanceOf(ForbiddenException.class);
        verify(profileRepository, never()).findAllByOrderByCreatedAtDesc();
    }

    @Test
    void listAllUsers_organizerCaller_throwsForbidden() {
        String organizerId = "user_organizer00000000000";
        when(profileRepository.findById(organizerId)).thenReturn(Optional.of(profile(organizerId, "organizer")));

        assertThatThrownBy(() -> service().listAllUsers(organizerId)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void listAllUsers_callerWithNoProfileRow_throwsForbidden() {
        when(profileRepository.findById("user_unknown0000000000000")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().listAllUsers("user_unknown0000000000000"))
            .isInstanceOf(ForbiddenException.class);
    }

    // ---- getPlatformRevenue ----

    @Test
    void getPlatformRevenue_asAdmin_returnsAggregateFromRepository() {
        when(profileRepository.findById(ADMIN_ID)).thenReturn(Optional.of(profile(ADMIN_ID, "admin")));
        when(bookingRepository.sumConfirmedRevenue()).thenReturn(new BigDecimal("1500.00"));

        AdminRevenueDto result = service().getPlatformRevenue(ADMIN_ID);

        assertThat(result.totalRevenue()).isEqualByComparingTo("1500.00");
    }

    @Test
    void getPlatformRevenue_zeroQualifyingBookings_returnsZero() {
        when(profileRepository.findById(ADMIN_ID)).thenReturn(Optional.of(profile(ADMIN_ID, "admin")));
        when(bookingRepository.sumConfirmedRevenue()).thenReturn(BigDecimal.ZERO);

        assertThat(service().getPlatformRevenue(ADMIN_ID).totalRevenue()).isEqualByComparingTo("0");
    }

    @Test
    void getPlatformRevenue_nonAdminCaller_throwsForbidden_andNeverQueries() {
        String customerId = "user_customer000000000000";
        when(profileRepository.findById(customerId)).thenReturn(Optional.of(profile(customerId, "customer")));

        assertThatThrownBy(() -> service().getPlatformRevenue(customerId)).isInstanceOf(ForbiddenException.class);
        verify(bookingRepository, never()).sumConfirmedRevenue();
    }

    // ---- updateUserRole ----

    @Test
    void updateUserRole_asAdmin_updatesTargetRole() {
        String targetId = "user_target00000000000000";
        when(profileRepository.findById(ADMIN_ID)).thenReturn(Optional.of(profile(ADMIN_ID, "admin")));
        when(profileRepository.updateRole(targetId, "organizer")).thenReturn(1);

        service().updateUserRole(targetId, "organizer", ADMIN_ID);

        verify(profileRepository).updateRole(targetId, "organizer");
    }

    @Test
    void updateUserRole_nonAdminCaller_throwsForbidden_andNeverUpdates() {
        String customerId = "user_customer000000000000";
        when(profileRepository.findById(customerId)).thenReturn(Optional.of(profile(customerId, "customer")));

        assertThatThrownBy(() -> service().updateUserRole("user_target00000000000000", "admin", customerId))
            .isInstanceOf(ForbiddenException.class);
        verify(profileRepository, never()).updateRole(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void updateUserRole_organizerCaller_throwsForbidden() {
        String organizerId = "user_organizer00000000000";
        when(profileRepository.findById(organizerId)).thenReturn(Optional.of(profile(organizerId, "organizer")));

        assertThatThrownBy(() -> service().updateUserRole("user_target00000000000000", "admin", organizerId))
            .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void updateUserRole_nonexistentTarget_throwsProfileNotFound() {
        when(profileRepository.findById(ADMIN_ID)).thenReturn(Optional.of(profile(ADMIN_ID, "admin")));
        when(profileRepository.updateRole("user_doesNotExist00000000", "admin")).thenReturn(0);

        assertThatThrownBy(() -> service().updateUserRole("user_doesNotExist00000000", "admin", ADMIN_ID))
            .isInstanceOf(ProfileNotFoundException.class);
    }

    @Test
    void updateUserRole_adminCanChangeOwnRole_selfModificationAllowed() {
        // Preserves the original updateProfileRoleAction's behavior: no
        // check prevents an admin from modifying their own row, since the
        // AdminRoleSelect UI renders for every profile including the
        // caller's own.
        when(profileRepository.findById(ADMIN_ID)).thenReturn(Optional.of(profile(ADMIN_ID, "admin")));
        when(profileRepository.updateRole(ADMIN_ID, "customer")).thenReturn(1);

        service().updateUserRole(ADMIN_ID, "customer", ADMIN_ID);

        verify(profileRepository).updateRole(ADMIN_ID, "customer");
    }

    @Test
    void updateUserRole_adminCanChangeAnotherAdminsRole_noRestriction() {
        String otherAdminId = "user_otherAdmin00000000000";
        when(profileRepository.findById(ADMIN_ID)).thenReturn(Optional.of(profile(ADMIN_ID, "admin")));
        when(profileRepository.updateRole(otherAdminId, "customer")).thenReturn(1);

        service().updateUserRole(otherAdminId, "customer", ADMIN_ID);

        verify(profileRepository).updateRole(otherAdminId, "customer");
    }

    @Test
    void updateUserRole_repeatedUpdate_idempotent() {
        String targetId = "user_target00000000000000";
        when(profileRepository.findById(ADMIN_ID)).thenReturn(Optional.of(profile(ADMIN_ID, "admin")));
        when(profileRepository.updateRole(targetId, "organizer")).thenReturn(1);

        service().updateUserRole(targetId, "organizer", ADMIN_ID);
        service().updateUserRole(targetId, "organizer", ADMIN_ID);

        verify(profileRepository, org.mockito.Mockito.times(2)).updateRole(targetId, "organizer");
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
