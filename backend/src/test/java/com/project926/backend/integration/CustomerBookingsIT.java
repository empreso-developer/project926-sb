package com.project926.backend.integration;

import com.project926.backend.dto.CustomerBookingDto;
import com.project926.backend.entity.Profile;
import com.project926.backend.repository.ProfileRepository;
import com.project926.backend.service.CustomerBookingService;
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

/**
 * The mandatory real-database proof for the customer-bookings migration
 * (Category A gap closed): a mix of pending/confirmed/cancelled bookings,
 * multiple ticket types on one booking, a booking with a verified payment,
 * a booking with an unverified payment (row exists, razorpay_payment_id
 * still null), and a booking with NO payment row at all (the Razorpay
 * order-creation-failure edge case — see PaymentService.createOrder's
 * Javadoc) — all read back through CustomerBookingService against the real
 * dev database, plus cross-customer isolation.
 *
 * "Unauthenticated request" (item 3 of the required test matrix) is
 * covered at the HTTP/security layer by CustomerControllerTest
 * (@WebMvcTest) — this class, like every other dev-DB IT in this codebase
 * (see AttendeeListingIT, CheckInIT), calls the service directly and has
 * no HTTP layer to be unauthenticated against.
 *
 * All rows created here are deleted in @AfterEach with explicit residue
 * verification. Gated behind SUPABASE_DB_PASSWORD; targets ONLY
 * memdvuuszsistdjckcfp — never production.
 */
@SpringBootTest(
    classes = CustomerBookingsIT.TestConfig.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@ActiveProfiles("dev")
@EnabledIfEnvironmentVariable(named = "SUPABASE_DB_PASSWORD", matches = ".+")
class CustomerBookingsIT {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = Profile.class)
    @EnableJpaRepositories(basePackageClasses = ProfileRepository.class)
    @ComponentScan(basePackages = "com.project926.backend.repository")
    @Import(CustomerBookingService.class)
    static class TestConfig {
    }

    @Autowired
    private CustomerBookingService customerBookingService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final String organizerId = "user_it_cb_organizer_" + UUID.randomUUID().toString().substring(0, 8);
    private final String customer1Id = "user_it_cb_cust1_" + UUID.randomUUID().toString().substring(0, 8);
    private final String customer2Id = "user_it_cb_cust2_" + UUID.randomUUID().toString().substring(0, 8);
    private UUID eventId;
    private UUID ticketTypeAId;
    private UUID ticketTypeBId;
    private UUID confirmedBookingId;
    private UUID pendingBookingId;
    private UUID cancelledBookingId;
    private UUID otherCustomerBookingId;

    private void seed() {
        jdbcTemplate.update("INSERT INTO profiles (id, email, role) VALUES (?, ?, ?)",
            organizerId, organizerId + "@example.com", "organizer");
        jdbcTemplate.update("INSERT INTO profiles (id, email, role) VALUES (?, ?, ?)",
            customer1Id, customer1Id + "@example.com", "customer");
        jdbcTemplate.update("INSERT INTO profiles (id, email, role) VALUES (?, ?, ?)",
            customer2Id, customer2Id + "@example.com", "customer");

        eventId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO events (id, organizer_id, title, event_date, event_time, venue, city, status) " +
                "VALUES (?, ?, 'Customer Bookings IT Event', CURRENT_DATE + 30, '19:00:00', 'Venue', 'City', 'approved')",
            eventId, organizerId
        );

        ticketTypeAId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO ticket_types (id, event_id, name, price, quantity_total, quantity_sold) VALUES (?, ?, 'General', 100.00, 100, 3)",
            ticketTypeAId, eventId
        );
        ticketTypeBId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO ticket_types (id, event_id, name, price, quantity_total, quantity_sold) VALUES (?, ?, 'VIP', 300.00, 20, 3)",
            ticketTypeBId, eventId
        );

        // Oldest: confirmed, QR set, verified payment, two ticket types (2 + 3 = 5 total).
        confirmedBookingId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO bookings (id, reference, customer_id, event_id, status, total_amount, qr_code, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, 'confirmed', 1100.00, 'data:image/png;base64,xxx', now() - interval '2 minutes', now())",
            confirmedBookingId, "CBIT-" + confirmedBookingId.toString().substring(0, 8).toUpperCase(), customer1Id, eventId
        );
        insertItem(confirmedBookingId, ticketTypeAId, 2, "100.00");
        insertItem(confirmedBookingId, ticketTypeBId, 3, "300.00");
        UUID confirmedPaymentId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO payments (id, booking_id, razorpay_order_id, razorpay_payment_id, amount, currency, status, created_at, updated_at) " +
                "VALUES (?, ?, 'order_it_cb_1', 'pay_it_cb_1', 1100.00, 'INR', 'paid', now(), now())",
            confirmedPaymentId, confirmedBookingId
        );

        // Middle: pending, payment row exists but not yet verified (razorpay_payment_id still null).
        pendingBookingId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO bookings (id, reference, customer_id, event_id, status, total_amount, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, 'pending', 100.00, now() - interval '1 minute', now())",
            pendingBookingId, "CBIT-" + pendingBookingId.toString().substring(0, 8).toUpperCase(), customer1Id, eventId
        );
        insertItem(pendingBookingId, ticketTypeAId, 1, "100.00");
        jdbcTemplate.update(
            "INSERT INTO payments (id, booking_id, razorpay_order_id, amount, currency, status, created_at, updated_at) " +
                "VALUES (?, ?, 'order_it_cb_2', 100.00, 'INR', 'created', now(), now())",
            UUID.randomUUID(), pendingBookingId
        );

        // Newest: cancelled, NO payment row at all (Razorpay order-creation failure edge case).
        cancelledBookingId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO bookings (id, reference, customer_id, event_id, status, total_amount, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, 'cancelled', 300.00, now(), now())",
            cancelledBookingId, "CBIT-" + cancelledBookingId.toString().substring(0, 8).toUpperCase(), customer1Id, eventId
        );
        insertItem(cancelledBookingId, ticketTypeBId, 1, "300.00");

        // A different customer's booking — must never appear in customer1's list.
        otherCustomerBookingId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO bookings (id, reference, customer_id, event_id, status, total_amount, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, 'confirmed', 100.00, now(), now())",
            otherCustomerBookingId, "CBIT-" + otherCustomerBookingId.toString().substring(0, 8).toUpperCase(), customer2Id, eventId
        );
        insertItem(otherCustomerBookingId, ticketTypeAId, 1, "100.00");
    }

    private void insertItem(UUID bookingId, UUID ticketTypeId, int quantity, String unitPrice) {
        jdbcTemplate.update(
            "INSERT INTO booking_items (id, booking_id, ticket_type_id, quantity, unit_price, subtotal) VALUES (?, ?, ?, ?, ?, ?)",
            UUID.randomUUID(), bookingId, ticketTypeId, quantity, new BigDecimal(unitPrice),
            new BigDecimal(unitPrice).multiply(BigDecimal.valueOf(quantity))
        );
    }

    @AfterEach
    void cleanUp() {
        if (eventId != null) {
            jdbcTemplate.update("DELETE FROM bookings WHERE event_id = ?", eventId);
            jdbcTemplate.update("DELETE FROM events WHERE id = ?", eventId);
        }
        jdbcTemplate.update("DELETE FROM profiles WHERE id IN (?, ?, ?)", organizerId, customer1Id, customer2Id);

        Integer remainingBookings = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM bookings WHERE customer_id IN (?, ?)", Integer.class, customer1Id, customer2Id);
        Integer remainingEvents = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM events WHERE organizer_id = ?", Integer.class, organizerId);
        Integer remainingProfiles = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM profiles WHERE id IN (?, ?, ?)", Integer.class,
            organizerId, customer1Id, customer2Id);
        assertThat(remainingBookings).isZero();
        assertThat(remainingEvents).isZero();
        assertThat(remainingProfiles).isZero();
    }

    @Test
    void listOwnBookings_returnsOnlyThisCustomersBookings_neverAnotherCustomers() {
        seed();

        List<CustomerBookingDto> result = customerBookingService.listOwnBookings(customer1Id);

        assertThat(result).hasSize(3);
        assertThat(result).extracting(CustomerBookingDto::id).doesNotContain(otherCustomerBookingId);

        List<CustomerBookingDto> otherResult = customerBookingService.listOwnBookings(customer2Id);
        assertThat(otherResult).hasSize(1);
        assertThat(otherResult.get(0).id()).isEqualTo(otherCustomerBookingId);
    }

    @Test
    void listOwnBookings_customerWithZeroBookings_returnsEmptyList() {
        seed();
        String noBookingsCustomerId = "user_it_cb_nobookings_" + UUID.randomUUID().toString().substring(0, 8);

        List<CustomerBookingDto> result = customerBookingService.listOwnBookings(noBookingsCustomerId);

        assertThat(result).isEmpty();
    }

    @Test
    void listOwnBookings_orderedByCreatedAtDescending() {
        seed();

        List<CustomerBookingDto> result = customerBookingService.listOwnBookings(customer1Id);

        assertThat(result).extracting(CustomerBookingDto::id)
            .containsExactly(cancelledBookingId, pendingBookingId, confirmedBookingId);
    }

    @Test
    void listOwnBookings_allStatusesReturned_noFiltering() {
        seed();

        List<CustomerBookingDto> result = customerBookingService.listOwnBookings(customer1Id);

        assertThat(result).extracting(CustomerBookingDto::status)
            .containsExactlyInAnyOrder("confirmed", "pending", "cancelled");
    }

    @Test
    void listOwnBookings_correctEventAndTicketInformation() {
        seed();

        CustomerBookingDto confirmed = customerBookingService.listOwnBookings(customer1Id).stream()
            .filter(b -> b.id().equals(confirmedBookingId)).findFirst().orElseThrow();

        assertThat(confirmed.event()).isNotNull();
        assertThat(confirmed.event().id()).isEqualTo(eventId);
        assertThat(confirmed.event().title()).isEqualTo("Customer Bookings IT Event");
        assertThat(confirmed.event().venue()).isEqualTo("Venue");
        assertThat(confirmed.event().city()).isEqualTo("City");
        assertThat(confirmed.ticketQuantity()).isEqualTo(5); // 2 (General) + 3 (VIP)
        assertThat(confirmed.qrCode()).isEqualTo("data:image/png;base64,xxx");
    }

    @Test
    void listOwnBookings_verifiedPayment_razorpayPaymentIdPresent() {
        seed();

        CustomerBookingDto confirmed = customerBookingService.listOwnBookings(customer1Id).stream()
            .filter(b -> b.id().equals(confirmedBookingId)).findFirst().orElseThrow();

        assertThat(confirmed.payment()).isNotNull();
        assertThat(confirmed.payment().razorpayPaymentId()).isEqualTo("pay_it_cb_1");
    }

    @Test
    void listOwnBookings_unverifiedPayment_paymentPresentButRazorpayIdNull() {
        seed();

        CustomerBookingDto pending = customerBookingService.listOwnBookings(customer1Id).stream()
            .filter(b -> b.id().equals(pendingBookingId)).findFirst().orElseThrow();

        assertThat(pending.payment()).isNotNull();
        assertThat(pending.payment().razorpayPaymentId()).isNull();
        assertThat(pending.ticketQuantity()).isEqualTo(1);
    }

    @Test
    void listOwnBookings_noPaymentRowAtAll_paymentIsNull() {
        seed();

        CustomerBookingDto cancelled = customerBookingService.listOwnBookings(customer1Id).stream()
            .filter(b -> b.id().equals(cancelledBookingId)).findFirst().orElseThrow();

        assertThat(cancelled.payment()).isNull();
        assertThat(cancelled.qrCode()).isNull();
    }

    @Test
    void listOwnBookings_multipleBookings_eachWithCorrectOwnData() {
        seed();

        List<CustomerBookingDto> result = customerBookingService.listOwnBookings(customer1Id);

        assertThat(result).hasSize(3);
        assertThat(result).allSatisfy(b -> assertThat(b.event().id()).isEqualTo(eventId));
        assertThat(result).extracting(CustomerBookingDto::ticketQuantity).containsExactly(1, 1, 5);
    }
}
