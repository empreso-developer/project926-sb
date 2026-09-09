package com.project926.backend.integration;

import com.project926.backend.dto.AttendeeListResponse;
import com.project926.backend.entity.Profile;
import com.project926.backend.repository.ProfileRepository;
import com.project926.backend.service.AttendeeService;
import com.project926.backend.service.EventService;
import com.project926.backend.service.ProfileService;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase F's mandatory real-database proof for attendee listing (Section 18):
 * a mix of checked-in and not-checked-in confirmed bookings, multiple ticket
 * types, and a zero-attendee event, all read back through
 * AttendeeService/EventService against the real dev database. Also proves
 * the requireEventOrganizerOrAdmin authorization rule (organizer OR admin,
 * strangers rejected) with real Profile rows, not mocks.
 *
 * All rows created here are deleted in @AfterEach with explicit residue
 * verification, matching CheckInIT's pattern. Gated behind
 * SUPABASE_DB_PASSWORD; targets ONLY memdvuuszsistdjckcfp.
 */
@SpringBootTest(
    classes = AttendeeListingIT.TestConfig.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@ActiveProfiles("dev")
@EnabledIfEnvironmentVariable(named = "SUPABASE_DB_PASSWORD", matches = ".+")
class AttendeeListingIT {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = Profile.class)
    @EnableJpaRepositories(basePackageClasses = ProfileRepository.class)
    @ComponentScan(basePackages = "com.project926.backend.repository")
    @Import({AttendeeService.class, EventService.class, ProfileService.class})
    static class TestConfig {
    }

    @Autowired
    private AttendeeService attendeeService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final String organizerId = "user_it_al_organizer_" + UUID.randomUUID().toString().substring(0, 8);
    private final String adminId = "user_it_al_admin_" + UUID.randomUUID().toString().substring(0, 8);
    private final String strangerId = "user_it_al_stranger_" + UUID.randomUUID().toString().substring(0, 8);
    private final String customer1Id = "user_it_al_cust1_" + UUID.randomUUID().toString().substring(0, 8);
    private final String customer2Id = "user_it_al_cust2_" + UUID.randomUUID().toString().substring(0, 8);
    private UUID eventId;
    private UUID emptyEventId;
    private UUID generalTicketTypeId;
    private UUID vipTicketTypeId;

    private void seed() {
        jdbcTemplate.update("INSERT INTO profiles (id, email, first_name, last_name, role) VALUES (?, ?, ?, ?, ?)",
            organizerId, organizerId + "@example.com", "Organizer", "Owner", "organizer");
        jdbcTemplate.update("INSERT INTO profiles (id, email, role) VALUES (?, ?, ?)",
            adminId, adminId + "@example.com", "admin");
        jdbcTemplate.update("INSERT INTO profiles (id, email, role) VALUES (?, ?, ?)",
            strangerId, strangerId + "@example.com", "organizer");
        jdbcTemplate.update("INSERT INTO profiles (id, email, first_name, last_name, role) VALUES (?, ?, ?, ?, ?)",
            customer1Id, customer1Id + "@example.com", "Ada", "Lovelace", "customer");
        jdbcTemplate.update("INSERT INTO profiles (id, email, first_name, last_name, role) VALUES (?, ?, ?, ?, ?)",
            customer2Id, customer2Id + "@example.com", "Grace", "Hopper", "customer");

        eventId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO events (id, organizer_id, title, event_date, event_time, venue, city, status) " +
                "VALUES (?, ?, 'Attendee Listing IT Event', CURRENT_DATE + 30, '19:00:00', 'Venue', 'City', 'approved')",
            eventId, organizerId
        );
        emptyEventId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO events (id, organizer_id, title, event_date, event_time, venue, city, status) " +
                "VALUES (?, ?, 'Empty Event', CURRENT_DATE + 30, '19:00:00', 'Venue', 'City', 'approved')",
            emptyEventId, organizerId
        );

        generalTicketTypeId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO ticket_types (id, event_id, name, price, quantity_total, quantity_sold) VALUES (?, ?, 'General', 50.00, 100, 3)",
            generalTicketTypeId, eventId
        );
        vipTicketTypeId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO ticket_types (id, event_id, name, price, quantity_total, quantity_sold) VALUES (?, ?, 'VIP', 200.00, 20, 1)",
            vipTicketTypeId, eventId
        );

        // Booking 1: customer1, checked in, 2x General + 1x VIP.
        UUID booking1 = insertConfirmedBooking(customer1Id, eventId, "checked_in_at", true);
        insertBookingItem(booking1, generalTicketTypeId, 2, "50.00");
        insertBookingItem(booking1, vipTicketTypeId, 1, "200.00");

        // Booking 2: customer2, not checked in, 1x General.
        UUID booking2 = insertConfirmedBooking(customer2Id, eventId, null, false);
        insertBookingItem(booking2, generalTicketTypeId, 1, "50.00");

        // Booking 3: customer1 again, pending (not confirmed) — must NOT appear in listing/stats.
        insertPendingBooking(customer1Id, eventId);
    }

    private UUID insertConfirmedBooking(String customerId, UUID eventId, String checkedInColumn, boolean checkedIn) {
        UUID bookingId = UUID.randomUUID();
        if (checkedIn) {
            jdbcTemplate.update(
                "INSERT INTO bookings (id, reference, customer_id, event_id, status, total_amount, checked_in_at, checked_in_by, created_at, updated_at) " +
                    "VALUES (?, ?, ?, ?, 'confirmed', 100.00, now(), ?, now(), now())",
                bookingId, "ALIT-" + bookingId.toString().substring(0, 8).toUpperCase(), customerId, eventId, organizerId
            );
        } else {
            jdbcTemplate.update(
                "INSERT INTO bookings (id, reference, customer_id, event_id, status, total_amount, created_at, updated_at) " +
                    "VALUES (?, ?, ?, ?, 'confirmed', 100.00, now(), now())",
                bookingId, "ALIT-" + bookingId.toString().substring(0, 8).toUpperCase(), customerId, eventId
            );
        }
        return bookingId;
    }

    private void insertPendingBooking(String customerId, UUID eventId) {
        UUID bookingId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO bookings (id, reference, customer_id, event_id, status, total_amount, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, 'pending', 50.00, now(), now())",
            bookingId, "ALIT-" + bookingId.toString().substring(0, 8).toUpperCase(), customerId, eventId
        );
    }

    private void insertBookingItem(UUID bookingId, UUID ticketTypeId, int quantity, String unitPrice) {
        jdbcTemplate.update(
            "INSERT INTO booking_items (id, booking_id, ticket_type_id, quantity, unit_price, subtotal) VALUES (?, ?, ?, ?, ?, ?)",
            UUID.randomUUID(), bookingId, ticketTypeId, quantity, new java.math.BigDecimal(unitPrice),
            new java.math.BigDecimal(unitPrice).multiply(java.math.BigDecimal.valueOf(quantity))
        );
    }

    @AfterEach
    void cleanUp() {
        if (eventId != null) {
            jdbcTemplate.update("DELETE FROM bookings WHERE event_id IN (?, ?)", eventId, emptyEventId);
            jdbcTemplate.update("DELETE FROM events WHERE id IN (?, ?)", eventId, emptyEventId);
        }
        jdbcTemplate.update("DELETE FROM profiles WHERE id IN (?, ?, ?, ?, ?)",
            organizerId, adminId, strangerId, customer1Id, customer2Id);

        Integer remainingBookings = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM bookings WHERE customer_id IN (?, ?)", Integer.class, customer1Id, customer2Id);
        Integer remainingEvents = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM events WHERE organizer_id = ?", Integer.class, organizerId);
        Integer remainingProfiles = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM profiles WHERE id IN (?, ?, ?, ?, ?)", Integer.class,
            organizerId, adminId, strangerId, customer1Id, customer2Id);
        assertThat(remainingBookings).isZero();
        assertThat(remainingEvents).isZero();
        assertThat(remainingProfiles).isZero();
    }

    @Test
    void listAttendees_asOwningOrganizer_returnsOnlyConfirmedBookings_withCorrectQuantityWeightedStats() {
        seed();

        AttendeeListResponse response = attendeeService.listAttendees(eventId, organizerId, null, null, null);

        // Only the 2 confirmed bookings, never the pending one.
        assertThat(response.attendees()).hasSize(2);
        assertThat(response.totalCount()).isEqualTo(2);

        // totalSold = 2(General)+1(VIP)+1(General) = 4; checkedInQty = booking1's 3 (2 General + 1 VIP); notCheckedInQty = booking2's 1.
        assertThat(response.stats().totalSold()).isEqualTo(4);
        assertThat(response.stats().checkedInQty()).isEqualTo(3);
        assertThat(response.stats().notCheckedInQty()).isEqualTo(1);
        assertThat(response.stats().checkInRate()).isEqualTo(75.0);

        var checkedInAttendee = response.attendees().stream()
            .filter(a -> a.checkedInAt() != null).findFirst().orElseThrow();
        assertThat(checkedInAttendee.customerName()).isEqualTo("Ada Lovelace");
        assertThat(checkedInAttendee.tickets()).hasSize(2);
    }

    @Test
    void listAttendees_asAdmin_succeedsWithoutBeingTheOwningOrganizer() {
        seed();

        AttendeeListResponse response = attendeeService.listAttendees(eventId, adminId, null, null, null);

        assertThat(response.attendees()).hasSize(2);
    }

    @Test
    void listAttendees_asUnrelatedOrganizer_throwsForbidden() {
        seed();

        assertThatThrownBy(() -> attendeeService.listAttendees(eventId, strangerId, null, null, null))
            .isInstanceOf(com.project926.backend.exception.ForbiddenException.class);
    }

    @Test
    void listAttendees_checkedInFilter_returnsOnlyTheCheckedInBooking() {
        seed();

        AttendeeListResponse response = attendeeService.listAttendees(eventId, organizerId, null, "checked_in", null);

        assertThat(response.attendees()).hasSize(1);
        assertThat(response.attendees().get(0).customerName()).isEqualTo("Ada Lovelace");
    }

    @Test
    void listAttendees_searchByCustomerLastName_matchesOnlyThatAttendee() {
        seed();

        AttendeeListResponse response = attendeeService.listAttendees(eventId, organizerId, null, null, "hopper");

        assertThat(response.attendees()).hasSize(1);
        assertThat(response.attendees().get(0).customerName()).isEqualTo("Grace Hopper");
    }

    @Test
    void listAttendees_searchByBookingReference_matches() {
        seed();
        String reference = jdbcTemplate.queryForObject(
            "SELECT reference FROM bookings WHERE customer_id = ? AND status = 'confirmed'", String.class, customer1Id);

        AttendeeListResponse response = attendeeService.listAttendees(eventId, organizerId, null, null, reference);

        assertThat(response.attendees()).hasSize(1);
    }

    @Test
    void listAttendees_zeroAttendeeEvent_returnsEmptyListWithZeroedStats() {
        seed();

        AttendeeListResponse response = attendeeService.listAttendees(emptyEventId, organizerId, null, null, null);

        assertThat(response.attendees()).isEmpty();
        assertThat(response.totalCount()).isZero();
        assertThat(response.stats().totalSold()).isZero();
        assertThat(response.stats().checkInRate()).isEqualTo(0.0);
    }
}
