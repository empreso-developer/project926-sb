package com.project926.backend.integration;

import com.project926.backend.dto.AdminUserDto;
import com.project926.backend.entity.Profile;
import com.project926.backend.exception.ForbiddenException;
import com.project926.backend.exception.ProfileNotFoundException;
import com.project926.backend.repository.ProfileRepository;
import com.project926.backend.service.AdminUserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The mandatory real-database proof for the admin users/revenue/role-
 * management migration (Category A gap closed): admin listing against real
 * profile rows, revenue summed from real confirmed/pending/cancelled
 * bookings (proving pending/cancelled are excluded exactly like the
 * original reduce()), and role updates (including self-modification,
 * modifying another admin, and a nonexistent target) against the real dev
 * database.
 *
 * "Unauthenticated request" is covered at the HTTP/security layer by
 * AdminUserControllerTest (@WebMvcTest) — this class, like every other
 * dev-DB IT in this codebase, calls the service directly and has no HTTP
 * layer to be unauthenticated against.
 *
 * All rows created here are deleted in @AfterEach with explicit residue
 * verification. Gated behind SUPABASE_DB_PASSWORD; targets ONLY
 * memdvuuszsistdjckcfp — never production.
 */
@SpringBootTest(
    classes = AdminUserManagementIT.TestConfig.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@ActiveProfiles("dev")
@EnabledIfEnvironmentVariable(named = "SUPABASE_DB_PASSWORD", matches = ".+")
class AdminUserManagementIT {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = Profile.class)
    @EnableJpaRepositories(basePackageClasses = ProfileRepository.class)
    @ComponentScan(basePackages = "com.project926.backend.repository")
    @Import(AdminUserService.class)
    static class TestConfig {
    }

    @Autowired
    private AdminUserService adminUserService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final String adminId = "user_it_admin_" + UUID.randomUUID().toString().substring(0, 8);
    private final String otherAdminId = "user_it_admin2_" + UUID.randomUUID().toString().substring(0, 8);
    private final String customerId = "user_it_cust_" + UUID.randomUUID().toString().substring(0, 8);
    private final String organizerId = "user_it_org_" + UUID.randomUUID().toString().substring(0, 8);
    private UUID eventId;
    private UUID confirmedBookingId;
    private UUID pendingBookingId;
    private UUID cancelledBookingId;

    private void seedProfiles() {
        jdbcTemplate.update("INSERT INTO profiles (id, email, role) VALUES (?, ?, ?)",
            adminId, adminId + "@example.com", "admin");
        jdbcTemplate.update("INSERT INTO profiles (id, email, role) VALUES (?, ?, ?)",
            otherAdminId, otherAdminId + "@example.com", "admin");
        jdbcTemplate.update("INSERT INTO profiles (id, email, first_name, last_name, role) VALUES (?, ?, ?, ?, ?)",
            customerId, customerId + "@example.com", "First", "Last", "customer");
        jdbcTemplate.update("INSERT INTO profiles (id, email, role) VALUES (?, ?, ?)",
            organizerId, organizerId + "@example.com", "organizer");
    }

    private void seedBookingsForRevenue() {
        jdbcTemplate.update("INSERT INTO profiles (id, email, role) VALUES (?, ?, ?)",
            organizerId, organizerId + "@example.com", "organizer");
        eventId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO events (id, organizer_id, title, event_date, event_time, venue, city, status) " +
                "VALUES (?, ?, 'Admin Revenue IT Event', CURRENT_DATE + 30, '19:00:00', 'Venue', 'City', 'approved')",
            eventId, organizerId
        );
        jdbcTemplate.update("INSERT INTO profiles (id, email, role) VALUES (?, ?, ?)",
            customerId, customerId + "@example.com", "customer");

        confirmedBookingId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO bookings (id, reference, customer_id, event_id, status, total_amount) VALUES (?, ?, ?, ?, 'confirmed', 750.25)",
            confirmedBookingId, "ARIT-" + confirmedBookingId.toString().substring(0, 8).toUpperCase(), customerId, eventId
        );
        pendingBookingId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO bookings (id, reference, customer_id, event_id, status, total_amount) VALUES (?, ?, ?, ?, 'pending', 999999.00)",
            pendingBookingId, "ARIT-" + pendingBookingId.toString().substring(0, 8).toUpperCase(), customerId, eventId
        );
        cancelledBookingId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO bookings (id, reference, customer_id, event_id, status, total_amount) VALUES (?, ?, ?, ?, 'cancelled', 888888.00)",
            cancelledBookingId, "ARIT-" + cancelledBookingId.toString().substring(0, 8).toUpperCase(), customerId, eventId
        );
    }

    @AfterEach
    void cleanUp() {
        if (eventId != null) {
            jdbcTemplate.update("DELETE FROM bookings WHERE event_id = ?", eventId);
            jdbcTemplate.update("DELETE FROM events WHERE id = ?", eventId);
        }
        jdbcTemplate.update("DELETE FROM profiles WHERE id IN (?, ?, ?, ?)",
            adminId, otherAdminId, customerId, organizerId);

        Integer remainingProfiles = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM profiles WHERE id IN (?, ?, ?, ?)", Integer.class,
            adminId, otherAdminId, customerId, organizerId);
        assertThat(remainingProfiles).isZero();

        if (eventId != null) {
            Integer remainingBookings = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM bookings WHERE event_id = ?", Integer.class, eventId);
            Integer remainingEvents = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM events WHERE id = ?", Integer.class, eventId);
            assertThat(remainingBookings).isZero();
            assertThat(remainingEvents).isZero();
        }
    }

    // ---- listAllUsers ----

    @Test
    void listAllUsers_admin_seesSeededProfiles_orderedByCreatedAtDesc() {
        seedProfiles();

        List<AdminUserDto> result = adminUserService.listAllUsers(adminId);

        assertThat(result).extracting(AdminUserDto::id)
            .contains(adminId, otherAdminId, customerId, organizerId);
        // organizerId inserted last among the four -> should appear before the earlier three.
        int organizerIndex = indexOfId(result, organizerId);
        int adminIndex = indexOfId(result, adminId);
        assertThat(organizerIndex).isLessThan(adminIndex);
    }

    @Test
    void listAllUsers_returnedFieldsMatchContract() {
        seedProfiles();

        AdminUserDto customer = adminUserService.listAllUsers(adminId).stream()
            .filter(u -> u.id().equals(customerId)).findFirst().orElseThrow();

        assertThat(customer.email()).isEqualTo(customerId + "@example.com");
        assertThat(customer.firstName()).isEqualTo("First");
        assertThat(customer.lastName()).isEqualTo("Last");
        assertThat(customer.role()).isEqualTo("customer");
        assertThat(customer.createdAt()).isNotNull();
    }

    @Test
    void listAllUsers_customerCaller_throwsForbidden() {
        seedProfiles();

        assertThatThrownBy(() -> adminUserService.listAllUsers(customerId))
            .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void listAllUsers_organizerCaller_throwsForbidden() {
        seedProfiles();

        assertThatThrownBy(() -> adminUserService.listAllUsers(organizerId))
            .isInstanceOf(ForbiddenException.class);
    }

    private static int indexOfId(List<AdminUserDto> list, String id) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id().equals(id)) return i;
        }
        throw new AssertionError("id not found: " + id);
    }

    // ---- revenue ----

    @Test
    void getPlatformRevenue_excludesPendingAndCancelled_isolatedDeltaCheck() {
        // Must NOT include the pending (999999.00) or cancelled (888888.00)
        // booking amounts -- only the confirmed (750.25) one. Asserted as an
        // isolated delta since the shared dev DB may hold other confirmed
        // bookings from other tests.
        jdbcTemplate.update("INSERT INTO profiles (id, email, role) VALUES (?, ?, ?)",
            adminId, adminId + "@example.com", "admin");

        BigDecimal before = adminUserService.getPlatformRevenue(adminId).totalRevenue();
        seedBookingsForRevenue();
        BigDecimal after = adminUserService.getPlatformRevenue(adminId).totalRevenue();

        assertThat(after.subtract(before)).isEqualByComparingTo("750.25");
    }

    @Test
    void getPlatformRevenue_nonAdminCaller_throwsForbidden() {
        seedProfiles();

        assertThatThrownBy(() -> adminUserService.getPlatformRevenue(customerId))
            .isInstanceOf(ForbiddenException.class);
    }

    // ---- role update ----

    @Test
    void updateUserRole_changesTargetRoleInDatabase() {
        seedProfiles();

        adminUserService.updateUserRole(customerId, "organizer", adminId);

        String storedRole = jdbcTemplate.queryForObject(
            "SELECT role FROM profiles WHERE id = ?", String.class, customerId);
        assertThat(storedRole).isEqualTo("organizer");
    }

    @Test
    void updateUserRole_nonAdminCaller_throwsForbidden_targetUnchanged() {
        seedProfiles();

        assertThatThrownBy(() -> adminUserService.updateUserRole(customerId, "admin", organizerId))
            .isInstanceOf(ForbiddenException.class);

        String storedRole = jdbcTemplate.queryForObject(
            "SELECT role FROM profiles WHERE id = ?", String.class, customerId);
        assertThat(storedRole).isEqualTo("customer");
    }

    @Test
    void updateUserRole_nonexistentTarget_throwsProfileNotFound() {
        seedProfiles();
        String nonexistentId = "user_it_doesNotExist_" + UUID.randomUUID().toString().substring(0, 8);

        assertThatThrownBy(() -> adminUserService.updateUserRole(nonexistentId, "admin", adminId))
            .isInstanceOf(ProfileNotFoundException.class);
    }

    @Test
    void updateUserRole_adminChangesOwnRole_selfModificationSucceeds() {
        seedProfiles();

        adminUserService.updateUserRole(adminId, "organizer", adminId);

        String storedRole = jdbcTemplate.queryForObject(
            "SELECT role FROM profiles WHERE id = ?", String.class, adminId);
        assertThat(storedRole).isEqualTo("organizer");

        // Restore role so cleanup's residue assertions aren't affected by
        // this test's own mutation (role isn't checked there, but keep the
        // row consistent for clarity).
        jdbcTemplate.update("UPDATE profiles SET role = 'admin' WHERE id = ?", adminId);
    }

    @Test
    void updateUserRole_adminChangesAnotherAdminsRole_noRestriction() {
        seedProfiles();

        adminUserService.updateUserRole(otherAdminId, "customer", adminId);

        String storedRole = jdbcTemplate.queryForObject(
            "SELECT role FROM profiles WHERE id = ?", String.class, otherAdminId);
        assertThat(storedRole).isEqualTo("customer");
    }

    @Test
    void updateUserRole_repeatedUpdate_idempotent() {
        seedProfiles();

        adminUserService.updateUserRole(customerId, "organizer", adminId);
        adminUserService.updateUserRole(customerId, "organizer", adminId);

        String storedRole = jdbcTemplate.queryForObject(
            "SELECT role FROM profiles WHERE id = ?", String.class, customerId);
        assertThat(storedRole).isEqualTo("organizer");
    }
}
