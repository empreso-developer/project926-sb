package com.project926.backend.integration;

import com.project926.backend.dto.CreateEventRequest;
import com.project926.backend.dto.CreateTicketTypeRequest;
import com.project926.backend.dto.EventDto;
import com.project926.backend.dto.TicketTypeDto;
import com.project926.backend.dto.UpdateEventRequest;
import com.project926.backend.entity.Profile;
import com.project926.backend.exception.EventNotFoundException;
import com.project926.backend.exception.ForbiddenException;
import com.project926.backend.repository.EventRepository;
import com.project926.backend.repository.ProfileRepository;
import com.project926.backend.repository.TicketTypeRepository;
import com.project926.backend.service.EventModerationService;
import com.project926.backend.service.EventService;
import com.project926.backend.service.ProfileService;
import com.project926.backend.service.TicketTypeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase C's real-database proof (Step 14 item 22 / Step 17): a genuine
 * create -> update -> ticket-type create -> publish -> admin approve ->
 * delete round trip through the actual EventService/TicketTypeService/
 * EventModerationService against the real DEVELOPMENT Supabase Postgres
 * database (memdvuuszsistdjckcfp — never production), not mocks.
 *
 * The dev database has no seed data, and the role check in
 * ProfileService.requireOrganizerOrAdminRole needs a real `profiles` row to
 * pass — so this test seeds one organizer profile (and reuses it as the
 * "admin" for the approve step, promoting its role) via a plain JdbcTemplate
 * insert/delete in setup/teardown. That is test fixture management only,
 * not application code, and every row this test creates (profile, event,
 * ticket type) is deleted again in @AfterEach — including via cleanup on
 * failure — so the dev database is left exactly as it was found.
 *
 * Same narrow-TestConfig pattern as DatabaseConnectivityIT/ProfileEventReadIT,
 * for the same reason (avoids SecurityConfig's HttpSecurity requirement
 * under webEnvironment=NONE). Imports only the four specific services this
 * test needs — NOT a package-wide @ComponentScan of `service`, which would
 * also sweep up PaymentService (Phase D) and its
 * BookingInventoryRepository/RazorpayGateway dependencies unnecessarily
 * (see ProfileEventReadIT's Javadoc for the concrete failure this caused).
 * Gated behind SUPABASE_DB_PASSWORD.
 */
@SpringBootTest(
    classes = EventWriteIT.TestConfig.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@ActiveProfiles("dev")
@EnabledIfEnvironmentVariable(named = "SUPABASE_DB_PASSWORD", matches = ".+")
class EventWriteIT {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = Profile.class)
    @EnableJpaRepositories(basePackageClasses = ProfileRepository.class)
    @Import({EventService.class, TicketTypeService.class, EventModerationService.class, ProfileService.class})
    static class TestConfig {
    }

    @Autowired
    private EventService eventService;

    @Autowired
    private TicketTypeService ticketTypeService;

    @Autowired
    private EventModerationService eventModerationService;

    @Autowired
    private ProfileService profileService;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private TicketTypeRepository ticketTypeRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final String organizerId = "user_it_organizer_" + UUID.randomUUID().toString().substring(0, 8);
    private UUID createdEventId;

    @AfterEach
    void cleanUp() {
        // Ticket types cascade-delete with their event (ON DELETE CASCADE),
        // but delete explicitly first for a clean, order-independent
        // teardown regardless of what each test actually got to.
        if (createdEventId != null) {
            ticketTypeRepository.findByEventId(createdEventId).forEach(ticketTypeRepository::delete);
            eventRepository.findById(createdEventId).ifPresent(eventRepository::delete);
        }
        jdbcTemplate.update("DELETE FROM profiles WHERE id = ?", organizerId);
    }

    private void seedOrganizerProfile() {
        jdbcTemplate.update(
            "INSERT INTO profiles (id, email, first_name, last_name, role) VALUES (?, ?, ?, ?, ?)",
            organizerId, organizerId + "@example.com", "IT", "Organizer", "organizer"
        );
    }

    @Test
    void fullOrganizerEventLifecycle_createUpdateTicketTypePublishDelete_againstRealDevDb() {
        seedOrganizerProfile();

        EventDto created = eventService.createEvent(organizerId, new CreateEventRequest(
            "IT Test Event", "created by EventWriteIT", LocalDate.now().plusMonths(1), LocalTime.of(19, 0),
            "Venue", "City", null
        ));
        createdEventId = created.id();
        assertThat(created.status()).isEqualTo("draft");
        assertThat(created.organizerId()).isEqualTo(organizerId);

        // Confirm it round-trips through a fresh read (real SELECT, not the
        // in-memory object just returned).
        EventDto reloaded = eventService.getOwnEventById(createdEventId, organizerId);
        assertThat(reloaded.title()).isEqualTo("IT Test Event");

        EventDto updated = eventService.updateEvent(createdEventId, organizerId, new UpdateEventRequest(
            "IT Test Event (updated)", "updated by EventWriteIT", LocalDate.now().plusMonths(2), LocalTime.of(20, 0),
            "New Venue", "New City", null
        ));
        assertThat(updated.title()).isEqualTo("IT Test Event (updated)");
        assertThat(updated.status()).isEqualTo("draft"); // update never touches status

        TicketTypeDto ticketType = ticketTypeService.createTicketType(createdEventId, organizerId,
            new CreateTicketTypeRequest("General", new BigDecimal("250.00"), 50, null, null));
        assertThat(ticketType.quantitySold()).isEqualTo(0); // never set on create

        EventDto published = eventService.publishEvent(createdEventId, organizerId);
        assertThat(published.status()).isEqualTo("published");

        // Promote the same seeded row to admin to prove approveEvent works
        // against a real row too, then confirm via a direct repository read.
        jdbcTemplate.update("UPDATE profiles SET role = 'admin' WHERE id = ?", organizerId);
        eventModerationService.approveEvent(createdEventId, organizerId);
        EventDto approved = eventService.getEventById(createdEventId);
        assertThat(approved.status()).isEqualTo("approved");

        ticketTypeService.deleteTicketType(createdEventId, ticketType.id(), organizerId);
        assertThat(ticketTypeRepository.findByEventId(createdEventId)).isEmpty();

        eventService.deleteEvent(createdEventId, organizerId);
        assertThat(eventRepository.findById(createdEventId)).isEmpty();
        createdEventId = null; // already deleted; nothing left for @AfterEach to do
    }

    @Test
    void createEvent_realDb_rejectsCustomerRole() {
        // No profile seeded at all -> ProfileService defaults to "customer"
        // against the real database, exactly like Phase B's
        // ProfileEventReadIT.profileLookupForUnknownClerkIdDefaultsToCustomer_realQuery.
        assertThatThrownBy(() -> eventService.createEvent(organizerId, new CreateEventRequest(
            "Should Not Be Created", null, LocalDate.now().plusDays(1), LocalTime.NOON, "Venue", "City", null
        ))).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void updateEvent_realDb_rejectsNonOwner() {
        seedOrganizerProfile();
        EventDto created = eventService.createEvent(organizerId, new CreateEventRequest(
            "Owned Event", null, LocalDate.now().plusDays(1), LocalTime.NOON, "Venue", "City", null
        ));
        createdEventId = created.id();

        assertThatThrownBy(() -> eventService.updateEvent(createdEventId, "user_it_someoneElse", new UpdateEventRequest(
            "Hijacked", null, LocalDate.now().plusDays(2), LocalTime.NOON, "Venue", "City", null
        ))).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void deleteEvent_realDb_throwsNotFoundForRandomId() {
        seedOrganizerProfile();
        assertThatThrownBy(() -> eventService.deleteEvent(UUID.randomUUID(), organizerId))
            .isInstanceOf(EventNotFoundException.class);
    }
}
