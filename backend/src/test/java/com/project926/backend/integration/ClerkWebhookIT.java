package com.project926.backend.integration;

import com.project926.backend.dto.ClerkEmailAddress;
import com.project926.backend.dto.ClerkUserData;
import com.project926.backend.entity.Profile;
import com.project926.backend.repository.BookingInventoryRepository;
import com.project926.backend.repository.ProfileRepository;
import com.project926.backend.service.ClerkWebhookService;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The mandatory real-database proof for the Clerk webhook migration —
 * exercises ClerkWebhookService against the real dev Postgres database
 * (memdvuuszsistdjckcfp — never production), including Phase 10's specific
 * regression requirement: a customer profile created by this webhook must
 * satisfy bookings.customer_id's FK, i.e. the exact failure mode from the
 * prior booking-FK investigation is proven fixed end-to-end (webhook ->
 * real profiles row -> real booking insert succeeds).
 *
 * All rows created here are deleted in @AfterEach with an explicit
 * residue check, same convention as every other *IT in this project.
 * Gated behind SUPABASE_DB_PASSWORD.
 */
@SpringBootTest(
    classes = ClerkWebhookIT.TestConfig.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@ActiveProfiles("dev")
@EnabledIfEnvironmentVariable(named = "SUPABASE_DB_PASSWORD", matches = ".+")
class ClerkWebhookIT {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = Profile.class)
    @EnableJpaRepositories(basePackageClasses = ProfileRepository.class)
    @ComponentScan(basePackages = "com.project926.backend.repository")
    @Import(ClerkWebhookService.class)
    static class TestConfig {
    }

    @Autowired
    private ClerkWebhookService webhookService;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private BookingInventoryRepository bookingInventoryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final String clerkUserId = "user_it_webhook_" + UUID.randomUUID().toString().substring(0, 8);
    private String organizerId;
    private UUID eventId;
    private UUID ticketTypeId;

    private ClerkUserData userData(String firstName, String lastName, String email, Map<String, Object> unsafeMetadata) {
        return new ClerkUserData(
            clerkUserId,
            List.of(new ClerkEmailAddress("email_1", email)),
            "email_1",
            firstName,
            lastName,
            Map.of(),
            unsafeMetadata);
    }

    @AfterEach
    void cleanUp() {
        if (eventId != null) {
            jdbcTemplate.update("DELETE FROM bookings WHERE event_id = ?", eventId);
            jdbcTemplate.update("DELETE FROM events WHERE id = ?", eventId);
        }
        jdbcTemplate.update("DELETE FROM profiles WHERE id = ?", clerkUserId);
        if (organizerId != null) {
            jdbcTemplate.update("DELETE FROM profiles WHERE id = ?", organizerId);
        }

        Integer remainingProfiles = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM profiles WHERE id = ?", Integer.class, clerkUserId);
        assertThat(remainingProfiles).isZero();
    }

    // ---- user.created ----

    @Test
    void userCreated_createsRealProfileRow() {
        webhookService.process("user.created", userData("Ada", "Lovelace", "ada@example.com", Map.of()));

        Profile profile = profileRepository.findById(clerkUserId).orElseThrow();
        assertThat(profile.getEmail()).isEqualTo("ada@example.com");
        assertThat(profile.getFirstName()).isEqualTo("Ada");
        assertThat(profile.getLastName()).isEqualTo("Lovelace");
        assertThat(profile.getRole()).isEqualTo("customer");
        assertThat(profile.getCreatedAt()).isNotNull();
        assertThat(profile.getUpdatedAt()).isNotNull();
    }

    @Test
    void userCreated_withOrganizerRoleInMetadata_persistsThatRole() {
        webhookService.process("user.created",
            userData("Grace", "Hopper", "grace@example.com", Map.of("role", "organizer")));

        Profile profile = profileRepository.findById(clerkUserId).orElseThrow();
        assertThat(profile.getRole()).isEqualTo("organizer");
    }

    @Test
    void duplicateUserCreatedDelivery_remainsSafe_noDuplicateRow() {
        ClerkUserData data = userData("Ada", "Lovelace", "ada@example.com", Map.of());

        webhookService.process("user.created", data);
        webhookService.process("user.created", data);

        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM profiles WHERE id = ?", Integer.class, clerkUserId);
        assertThat(count).isEqualTo(1);
    }

    // ---- user.updated ----

    @Test
    void userUpdated_updatesExpectedFields_onExistingRow() {
        webhookService.process("user.created", userData("Old", "Name", "old@example.com", Map.of()));

        webhookService.process("user.updated", userData("New", "Name", "new@example.com", Map.of("role", "admin")));

        Profile profile = profileRepository.findById(clerkUserId).orElseThrow();
        assertThat(profile.getFirstName()).isEqualTo("New");
        assertThat(profile.getEmail()).isEqualTo("new@example.com");
        assertThat(profile.getRole()).isEqualTo("admin");
    }

    @Test
    void userUpdated_forUnknownUser_behavesAsUpsert_createsRow() {
        // The original Next.js route treats user.created and user.updated
        // identically (both call the same upsert) — an update for a user
        // this app never saw a "created" event for still results in a row,
        // matching that exact behavior rather than silently doing nothing.
        webhookService.process("user.updated", userData("First", "Time", "firsttime@example.com", Map.of()));

        Profile profile = profileRepository.findById(clerkUserId).orElseThrow();
        assertThat(profile.getEmail()).isEqualTo("firsttime@example.com");
    }

    @Test
    void duplicateUserUpdatedDelivery_remainsSafe() {
        webhookService.process("user.created", userData("Ada", "Lovelace", "ada@example.com", Map.of()));
        ClerkUserData update = userData("Ada", "L.", "ada2@example.com", Map.of());

        webhookService.process("user.updated", update);
        webhookService.process("user.updated", update);

        Profile profile = profileRepository.findById(clerkUserId).orElseThrow();
        assertThat(profile.getEmail()).isEqualTo("ada2@example.com");
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM profiles WHERE id = ?", Integer.class, clerkUserId);
        assertThat(count).isEqualTo(1);
    }

    // ---- user.deleted ----

    @Test
    void userDeleted_removesTheProfileRow() {
        webhookService.process("user.created", userData("Ada", "Lovelace", "ada@example.com", Map.of()));
        assertThat(profileRepository.findById(clerkUserId)).isPresent();

        webhookService.process("user.deleted", userData(null, null, null, Map.of()));

        assertThat(profileRepository.findById(clerkUserId)).isEmpty();
    }

    @Test
    void duplicateUserDeletedDelivery_secondCallSafeNoOp() {
        webhookService.process("user.created", userData("Ada", "Lovelace", "ada@example.com", Map.of()));

        webhookService.process("user.deleted", userData(null, null, null, Map.of()));
        webhookService.process("user.deleted", userData(null, null, null, Map.of())); // no throw

        assertThat(profileRepository.findById(clerkUserId)).isEmpty();
    }

    @Test
    void userDeleted_forUnknownUser_isSafeNoOp() {
        webhookService.process("user.deleted", userData(null, null, null, Map.of())); // no throw, no row ever existed

        assertThat(profileRepository.findById(clerkUserId)).isEmpty();
    }

    // ---- Phase 10: the booking-FK regression, proven end-to-end ----

    @Test
    void newlyCreatedCustomerProfile_satisfiesBookingCustomerIdForeignKey() {
        // 1. Clerk user.created arrives -> webhook creates the profile row
        // (exactly the step that was MISSING before this migration, which
        // is what caused bookings_customer_id_fkey to reject real
        // customers).
        webhookService.process("user.created", userData("New", "Customer", "newcustomer@example.com", Map.of()));
        assertThat(profileRepository.findById(clerkUserId)).isPresent();

        // 2. Seed a real event/ticket type to book against.
        organizerId = "user_it_webhook_org_" + UUID.randomUUID().toString().substring(0, 8);
        jdbcTemplate.update("INSERT INTO profiles (id, email, role) VALUES (?, ?, ?)",
            organizerId, organizerId + "@example.com", "organizer");
        eventId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO events (id, organizer_id, title, event_date, event_time, venue, city, status) " +
                "VALUES (?, ?, 'Webhook Regression Event', CURRENT_DATE + 30, '19:00:00', 'Venue', 'City', 'approved')",
            eventId, organizerId);
        ticketTypeId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO ticket_types (id, event_id, name, price, quantity_total, quantity_sold) " +
                "VALUES (?, ?, 'General', 100.00, 10, 0)",
            ticketTypeId, eventId);

        // 3. The exact call PaymentService.createOrder makes — proves the
        // FK now succeeds for a customer whose ONLY route into `profiles`
        // was this webhook.
        String itemsJson = "[{\"ticket_type_id\":\"" + ticketTypeId + "\",\"quantity\":1}]";
        UUID bookingId = bookingInventoryRepository.createBookingFromItems(clerkUserId, eventId, itemsJson);

        assertThat(bookingId).isNotNull();
        String storedCustomerId = jdbcTemplate.queryForObject(
            "SELECT customer_id FROM bookings WHERE id = ?", String.class, bookingId);
        assertThat(storedCustomerId).isEqualTo(clerkUserId);
    }
}
