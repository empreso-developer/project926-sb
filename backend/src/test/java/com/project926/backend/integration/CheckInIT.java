package com.project926.backend.integration;

import com.project926.backend.dto.CheckInResponse;
import com.project926.backend.entity.Profile;
import com.project926.backend.repository.ProfileRepository;
import com.project926.backend.service.CheckInService;
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
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase F's mandatory real-database proof for the two critical safety
 * requirements of QR check-in (Sections 8/9/16/17 of the Phase F brief):
 *
 * 1. Atomic, race-safe check-in: 10 concurrent check-in attempts against the
 *    SAME confirmed booking must result in EXACTLY 1 success — proven via
 *    real threads hitting CheckInService (and, through it, the real guarded
 *    UPDATE in BookingRepository.checkIn) against the real dev database, not
 *    a mocked repository.
 * 2. Wrong-event protection: a booking that genuinely belongs to event A
 *    cannot be checked in through event B's endpoint, and the event
 *    relationship enforced is the booking's ACTUAL database event_id — never
 *    anything supplied by the caller/QR — proven the same way.
 *
 * All rows created here (two profiles, two events, one ticket type, one
 * booking) are deleted in @AfterEach and this class asserts zero residue
 * afterward, per Step 19's explicit test-isolation requirement. Gated behind
 * SUPABASE_DB_PASSWORD like every other dev-DB IT — targets ONLY
 * memdvuuszsistdjckcfp (dev), never production.
 */
@SpringBootTest(
    classes = CheckInIT.TestConfig.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@ActiveProfiles("dev")
@EnabledIfEnvironmentVariable(named = "SUPABASE_DB_PASSWORD", matches = ".+")
class CheckInIT {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = Profile.class)
    @EnableJpaRepositories(basePackageClasses = ProfileRepository.class)
    @ComponentScan(basePackages = "com.project926.backend.repository")
    @Import(CheckInService.class)
    static class TestConfig {
    }

    @Autowired
    private CheckInService checkInService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final String organizerId = "user_it_ci_organizer_" + UUID.randomUUID().toString().substring(0, 8);
    private final String customerId = "user_it_ci_customer_" + UUID.randomUUID().toString().substring(0, 8);
    private UUID eventAId;
    private UUID eventBId;
    private UUID ticketTypeId;
    private UUID bookingId;

    private void seed() {
        jdbcTemplate.update("INSERT INTO profiles (id, email, role) VALUES (?, ?, ?)",
            organizerId, organizerId + "@example.com", "organizer");
        jdbcTemplate.update("INSERT INTO profiles (id, email, role) VALUES (?, ?, ?)",
            customerId, customerId + "@example.com", "customer");

        eventAId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO events (id, organizer_id, title, event_date, event_time, venue, city, status) " +
                "VALUES (?, ?, 'Check-in IT Event A', CURRENT_DATE + 30, '19:00:00', 'Venue A', 'City', 'approved')",
            eventAId, organizerId
        );
        eventBId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO events (id, organizer_id, title, event_date, event_time, venue, city, status) " +
                "VALUES (?, ?, 'Check-in IT Event B', CURRENT_DATE + 30, '19:00:00', 'Venue B', 'City', 'approved')",
            eventBId, organizerId
        );

        ticketTypeId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO ticket_types (id, event_id, name, price, quantity_total, quantity_sold) " +
                "VALUES (?, ?, 'General', 100.00, 10, 1)",
            ticketTypeId, eventAId
        );

        // A confirmed booking for event A, directly inserted — booking
        // creation itself is Phase D/E's RPC flow, not under test here.
        bookingId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO bookings (id, reference, customer_id, event_id, status, total_amount, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, 'confirmed', 100.00, now(), now())",
            bookingId, "CIIT-" + bookingId.toString().substring(0, 8).toUpperCase(), customerId, eventAId
        );
        jdbcTemplate.update(
            "INSERT INTO booking_items (id, booking_id, ticket_type_id, quantity, unit_price, subtotal) " +
                "VALUES (?, ?, ?, 1, 100.00, 100.00)",
            UUID.randomUUID(), bookingId, ticketTypeId
        );
    }

    @AfterEach
    void cleanUp() {
        if (eventAId != null) {
            jdbcTemplate.update("DELETE FROM bookings WHERE event_id IN (?, ?)", eventAId, eventBId);
            jdbcTemplate.update("DELETE FROM events WHERE id IN (?, ?)", eventAId, eventBId);
        }
        jdbcTemplate.update("DELETE FROM profiles WHERE id IN (?, ?)", organizerId, customerId);

        // Explicit residue verification (Step 19).
        Integer remainingBookings = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM bookings WHERE customer_id = ?", Integer.class, customerId);
        Integer remainingEvents = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM events WHERE organizer_id = ?", Integer.class, organizerId);
        Integer remainingProfiles = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM profiles WHERE id IN (?, ?)", Integer.class, organizerId, customerId);
        assertThat(remainingBookings).isZero();
        assertThat(remainingEvents).isZero();
        assertThat(remainingProfiles).isZero();
    }

    /**
     * Section 16's mandatory concurrency test: 10 concurrent check-in
     * attempts against the SAME booking via CheckInService (real guarded
     * UPDATE, real database, real threads) — exactly 1 must succeed, the
     * rest must be rejected/reported as duplicates, checked_in_at populated
     * exactly once, checked_in_by correctly attributed. Not a mocked
     * repository test.
     */
    @Test
    void concurrentCheckInAttempts_exactlyOneSucceeds_restReportAlreadyCheckedIn() throws Exception {
        seed();
        int concurrentAttempts = 10;

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger alreadyCheckedInCount = new AtomicInteger(0);

        ExecutorService pool = Executors.newFixedThreadPool(concurrentAttempts);
        try {
            List<Callable<Void>> tasks = IntStream.range(0, concurrentAttempts)
                .<Callable<Void>>mapToObj(i -> () -> {
                    CheckInResponse response = checkInService.checkIn(eventAId, organizerId, bookingId, null);
                    if (response.success() && "checked_in".equals(response.status())) {
                        successCount.incrementAndGet();
                    } else if ("already_checked_in".equals(response.status())) {
                        alreadyCheckedInCount.incrementAndGet();
                    }
                    return null;
                })
                .toList();

            List<Future<Void>> futures = pool.invokeAll(tasks, 60, TimeUnit.SECONDS);
            for (Future<Void> f : futures) {
                f.get(); // propagate any unexpected exception
            }
        } finally {
            pool.shutdown();
        }

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(alreadyCheckedInCount.get()).isEqualTo(concurrentAttempts - 1);

        // Direct DB read: checked_in_at populated exactly once, correct organizer attributed.
        var row = jdbcTemplate.queryForMap(
            "SELECT checked_in_at, checked_in_by FROM bookings WHERE id = ?", bookingId);
        assertThat(row.get("checked_in_at")).isNotNull();
        assertThat(row.get("checked_in_by")).isEqualTo(organizerId);
    }

    /**
     * Section 17's mandatory wrong-event test: a booking genuinely
     * belonging to event A cannot be checked in through event B's endpoint,
     * event A's booking remains unchecked afterward, and the SAME booking
     * legitimately succeeds through event A's own endpoint.
     */
    @Test
    void checkInViaWrongEventEndpoint_isRejected_bookingRemainsUncheckedUntilCorrectEventSucceeds() {
        seed();

        CheckInResponse wrongEventResponse = checkInService.checkIn(eventBId, organizerId, bookingId, null);

        assertThat(wrongEventResponse.success()).isFalse();
        assertThat(wrongEventResponse.status()).isEqualTo("wrong_event");

        var afterWrongAttempt = jdbcTemplate.queryForMap(
            "SELECT checked_in_at, checked_in_by FROM bookings WHERE id = ?", bookingId);
        assertThat(afterWrongAttempt.get("checked_in_at")).isNull();
        assertThat(afterWrongAttempt.get("checked_in_by")).isNull();

        CheckInResponse correctEventResponse = checkInService.checkIn(eventAId, organizerId, bookingId, null);

        assertThat(correctEventResponse.success()).isTrue();
        assertThat(correctEventResponse.status()).isEqualTo("checked_in");

        var afterCorrectAttempt = jdbcTemplate.queryForMap(
            "SELECT checked_in_at, checked_in_by FROM bookings WHERE id = ?", bookingId);
        assertThat(afterCorrectAttempt.get("checked_in_at")).isNotNull();
        assertThat(afterCorrectAttempt.get("checked_in_by")).isEqualTo(organizerId);
    }

    @Test
    void checkInByReference_findsAndChecksInBooking_realDbLookup() {
        seed();
        String reference = jdbcTemplate.queryForObject("SELECT reference FROM bookings WHERE id = ?", String.class, bookingId);

        CheckInResponse response = checkInService.checkIn(eventAId, organizerId, null, reference);

        assertThat(response.success()).isTrue();
        assertThat(response.status()).isEqualTo("checked_in");
        String checkedInBy = jdbcTemplate.queryForObject("SELECT checked_in_by FROM bookings WHERE id = ?", String.class, bookingId);
        assertThat(checkedInBy).isEqualTo(organizerId);
    }

    @Test
    void checkInNonexistentBooking_returnsInvalid_realDbLookupMiss() {
        seed();

        CheckInResponse response = checkInService.checkIn(eventAId, organizerId, UUID.randomUUID(), null);

        assertThat(response.success()).isFalse();
        assertThat(response.status()).isEqualTo("invalid");
    }
}
