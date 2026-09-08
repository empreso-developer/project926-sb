package com.project926.backend.integration;

import com.project926.backend.entity.Profile;
import com.project926.backend.exception.SoldOutException;
import com.project926.backend.repository.BookingInventoryRepository;
import com.project926.backend.repository.ProfileRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase D's real-database proof for Step 4/16/18/21: exercises the two
 * existing, UNMODIFIED Postgres functions (create_booking_from_items,
 * confirm_booking_and_commit_inventory) directly via BookingInventoryRepository
 * against the real DEVELOPMENT Supabase Postgres database
 * (memdvuuszsistdjckcfp — never production). No Razorpay call is made
 * anywhere in this class (Step 21: Razorpay is mocked in PaymentServiceTest,
 * never hit for real in any automated test) — this class tests only the
 * database side of the flow.
 *
 * Every row this test creates (a seeded organizer/customer profile, one
 * event, its ticket types, and any bookings/booking_items the RPCs create)
 * is deleted in @AfterEach, verified residue-free afterward the same way
 * Phase C's EventWriteIT was.
 *
 * Gated behind SUPABASE_DB_PASSWORD, same as every other dev-DB IT.
 */
@SpringBootTest(
    classes = BookingInventoryIT.TestConfig.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@ActiveProfiles("dev")
@EnabledIfEnvironmentVariable(named = "SUPABASE_DB_PASSWORD", matches = ".+")
class BookingInventoryIT {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = Profile.class)
    @EnableJpaRepositories(basePackageClasses = ProfileRepository.class)
    @ComponentScan(basePackages = "com.project926.backend.repository")
    static class TestConfig {
    }

    @Autowired
    private BookingInventoryRepository bookingInventoryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final String customerId = "user_it_customer_" + UUID.randomUUID().toString().substring(0, 8);
    private UUID eventId;
    private UUID ticketTypeId;

    private void seedEventAndTicketType(int quantityTotal) {
        jdbcTemplate.update(
            "INSERT INTO profiles (id, email, role) VALUES (?, ?, ?)",
            customerId, customerId + "@example.com", "customer"
        );
        eventId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO events (id, organizer_id, title, event_date, event_time, venue, city, status) " +
                "VALUES (?, ?, 'IT Test Event', CURRENT_DATE + 30, '19:00:00', 'Venue', 'City', 'approved')",
            eventId, customerId
        );
        ticketTypeId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO ticket_types (id, event_id, name, price, quantity_total, quantity_sold) " +
                "VALUES (?, ?, 'General', 100.00, ?, 0)",
            ticketTypeId, eventId, quantityTotal
        );
    }

    @AfterEach
    void cleanUp() {
        if (eventId != null) {
            // Explicit order matters here: booking_items.ticket_type_id is
            // ON DELETE RESTRICT (see supabase/schema.sql), while
            // ticket_types.event_id and bookings.event_id are both ON
            // DELETE CASCADE from events. A bare `DELETE FROM events`
            // triggers both sibling cascades from one statement, and
            // Postgres does not guarantee the bookings->booking_items
            // cascade completes before the ticket_types RESTRICT check
            // runs — deleting bookings (and therefore booking_items, via
            // its own cascade) explicitly FIRST avoids that race. This is
            // purely test-fixture cleanup ordering, not a schema or RPC
            // change.
            jdbcTemplate.update("DELETE FROM bookings WHERE event_id = ?", eventId);
            jdbcTemplate.update("DELETE FROM events WHERE id = ?", eventId);
        }
        jdbcTemplate.update("DELETE FROM profiles WHERE id = ?", customerId);
    }

    private String itemsJson(UUID ticketTypeId, int quantity) {
        return "[{\"ticket_type_id\":\"" + ticketTypeId + "\",\"quantity\":" + quantity + "}]";
    }

    @Test
    void createBookingFromItems_createsPendingBookingWithFifteenMinuteHold_withoutIncrementingQuantitySold() {
        seedEventAndTicketType(10);

        UUID bookingId = bookingInventoryRepository.createBookingFromItems(customerId, eventId, itemsJson(ticketTypeId, 3));

        assertThat(bookingId).isNotNull();
        var booking = jdbcTemplate.queryForMap("SELECT status, expires_at, total_amount FROM bookings WHERE id = ?", bookingId);
        assertThat(booking.get("status")).isEqualTo("pending");
        OffsetDateTime expiresAt = ((java.sql.Timestamp) booking.get("expires_at")).toInstant().atOffset(java.time.ZoneOffset.UTC);
        assertThat(expiresAt).isAfter(OffsetDateTime.now().plusMinutes(14)).isBefore(OffsetDateTime.now().plusMinutes(16));
        assertThat(((BigDecimal) booking.get("total_amount")).compareTo(new BigDecimal("300.00"))).isZero(); // 3 * 100.00

        Integer quantitySold = jdbcTemplate.queryForObject("SELECT quantity_sold FROM ticket_types WHERE id = ?", Integer.class, ticketTypeId);
        assertThat(quantitySold).isEqualTo(0); // NOT incremented at creation — this is the whole point of the hold model

        List<java.util.Map<String, Object>> items = jdbcTemplate.queryForList("SELECT quantity, unit_price FROM booking_items WHERE booking_id = ?", bookingId);
        assertThat(items).hasSize(1);
        assertThat(items.get(0).get("quantity")).isEqualTo(3);
    }

    @Test
    void createBookingFromItems_rejectsQuantityExceedingAvailability() {
        seedEventAndTicketType(2);

        assertThatThrownBy(() -> bookingInventoryRepository.createBookingFromItems(customerId, eventId, itemsJson(ticketTypeId, 3)))
            .hasMessageContaining("Not enough tickets available");
    }

    @Test
    void confirmBookingAndCommitInventory_incrementsQuantitySold_andMarksConfirmed() {
        seedEventAndTicketType(10);
        UUID bookingId = bookingInventoryRepository.createBookingFromItems(customerId, eventId, itemsJson(ticketTypeId, 4));

        boolean newlyConfirmed = bookingInventoryRepository.confirmBookingAndCommitInventory(bookingId, null);

        assertThat(newlyConfirmed).isTrue();
        Integer quantitySold = jdbcTemplate.queryForObject("SELECT quantity_sold FROM ticket_types WHERE id = ?", Integer.class, ticketTypeId);
        assertThat(quantitySold).isEqualTo(4);
        String status = jdbcTemplate.queryForObject("SELECT status FROM bookings WHERE id = ?", String.class, bookingId);
        assertThat(status).isEqualTo("confirmed");
    }

    @Test
    void confirmBookingAndCommitInventory_isIdempotent_duplicateCallDoesNotDoubleIncrement() {
        seedEventAndTicketType(10);
        UUID bookingId = bookingInventoryRepository.createBookingFromItems(customerId, eventId, itemsJson(ticketTypeId, 4));

        boolean first = bookingInventoryRepository.confirmBookingAndCommitInventory(bookingId, null);
        boolean second = bookingInventoryRepository.confirmBookingAndCommitInventory(bookingId, null);

        assertThat(first).isTrue();
        assertThat(second).isFalse(); // already confirmed -> safe no-op
        Integer quantitySold = jdbcTemplate.queryForObject("SELECT quantity_sold FROM ticket_types WHERE id = ?", Integer.class, ticketTypeId);
        assertThat(quantitySold).isEqualTo(4); // not 8
    }

    @Test
    void confirmBookingAndCommitInventory_soldOut_throwsAndDoesNotMutateAnythingElse() {
        seedEventAndTicketType(1);

        // Booking A takes the only slot, its hold is then backdated so a
        // second booking can be created past it (reproducing the exact
        // "hold lapsed, someone else's confirmed purchase took the seat"
        // scenario documented in supabase/migrations/20260903090000_fix_inventory_hold_race.sql).
        UUID bookingA = bookingInventoryRepository.createBookingFromItems(customerId, eventId, itemsJson(ticketTypeId, 1));
        jdbcTemplate.update("UPDATE bookings SET expires_at = now() - interval '1 hour' WHERE id = ?", bookingA);

        UUID bookingB = bookingInventoryRepository.createBookingFromItems(customerId, eventId, itemsJson(ticketTypeId, 1));

        // A confirms first (expiry is not checked by confirm — only status).
        assertThat(bookingInventoryRepository.confirmBookingAndCommitInventory(bookingA, null)).isTrue();

        assertThatThrownBy(() -> bookingInventoryRepository.confirmBookingAndCommitInventory(bookingB, null))
            .isInstanceOf(SoldOutException.class)
            .hasMessageStartingWith("SOLD_OUT");

        // The RPC's own transaction rolled back entirely on RAISE EXCEPTION
        // — quantity_sold stayed at exactly 1 (A's confirmation), and B's
        // booking is untouched (still pending, not confirmed).
        Integer quantitySold = jdbcTemplate.queryForObject("SELECT quantity_sold FROM ticket_types WHERE id = ?", Integer.class, ticketTypeId);
        assertThat(quantitySold).isEqualTo(1);
        String statusB = jdbcTemplate.queryForObject("SELECT status FROM bookings WHERE id = ?", String.class, bookingB);
        assertThat(statusB).isEqualTo("pending");
    }

    @Test
    void concurrentCreateAndConfirm_neverOversells() throws Exception {
        int quantityTotal = 5;
        int concurrentAttempts = 10;
        seedEventAndTicketType(quantityTotal);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger createRejectedCount = new AtomicInteger(0);

        ExecutorService pool = Executors.newFixedThreadPool(concurrentAttempts);
        try {
            List<Callable<Void>> tasks = java.util.stream.IntStream.range(0, concurrentAttempts)
                .<Callable<Void>>mapToObj(i -> () -> {
                    try {
                        UUID bookingId = bookingInventoryRepository.createBookingFromItems(customerId, eventId, itemsJson(ticketTypeId, 1));
                        boolean confirmed = bookingInventoryRepository.confirmBookingAndCommitInventory(bookingId, null);
                        if (confirmed) {
                            successCount.incrementAndGet();
                        }
                    } catch (RuntimeException e) {
                        // Expected for attempts beyond quantityTotal: either
                        // rejected at create-time (hold exceeds availability)
                        // or, less likely under this timing, SOLD_OUT at
                        // confirm-time. Either is a correctly-rejected attempt.
                        createRejectedCount.incrementAndGet();
                    }
                    return null;
                })
                .toList();

            List<Future<Void>> futures = pool.invokeAll(tasks, 60, TimeUnit.SECONDS);
            for (Future<Void> f : futures) {
                f.get(); // propagate any unexpected (non-business) exception
            }
        } finally {
            pool.shutdown();
        }

        Integer finalQuantitySold = jdbcTemplate.queryForObject("SELECT quantity_sold FROM ticket_types WHERE id = ?", Integer.class, ticketTypeId);

        // The core invariant Step 21 requires: never oversold.
        assertThat(finalQuantitySold).isLessThanOrEqualTo(quantityTotal);
        assertThat(finalQuantitySold).isEqualTo(successCount.get());
        assertThat(successCount.get()).isEqualTo(quantityTotal); // exactly 5 of 10 succeed
        assertThat(successCount.get() + createRejectedCount.get()).isEqualTo(concurrentAttempts);
    }
}
