package com.project926.backend.integration;

import com.project926.backend.dto.CreateOrderRequest;
import com.project926.backend.dto.CreateOrderResponse;
import com.project926.backend.dto.VerifyPaymentRequest;
import com.project926.backend.dto.VerifyPaymentResponse;
import com.project926.backend.entity.Profile;
import com.project926.backend.integration.qrcode.QrCodeGenerator;
import com.project926.backend.integration.razorpay.RazorpayGateway;
import com.project926.backend.integration.resend.ResendGateway;
import com.project926.backend.repository.ProfileRepository;
import com.project926.backend.service.PaymentService;
import com.project926.backend.service.TicketEmailService;
import com.razorpay.Order;
import com.razorpay.RazorpayException;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase E's end-to-end real-database proof (Step 19): a genuine
 * create-order -> verify (signature/capture check bypassed via mocked
 * RazorpayGateway, since Step 21 forbids real Razorpay calls in tests) ->
 * real confirm_booking_and_commit_inventory -> real QR generation and
 * persistence -> real email claim -> (mocked) Resend send round trip,
 * entirely through PaymentService/TicketEmailService against the real
 * DEVELOPMENT Supabase Postgres database (memdvuuszsistdjckcfp — never
 * production).
 *
 * Only RazorpayGateway and ResendGateway are mocked (Step 19/21: no real
 * Razorpay or Resend calls from automated tests) — every database
 * operation, including both existing RPCs, is real.
 */
@SpringBootTest(
    classes = TicketFlowIT.TestConfig.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@ActiveProfiles("dev")
@EnabledIfEnvironmentVariable(named = "SUPABASE_DB_PASSWORD", matches = ".+")
class TicketFlowIT {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = Profile.class)
    @EnableJpaRepositories(basePackageClasses = ProfileRepository.class)
    @ComponentScan(basePackages = "com.project926.backend.repository")
    @Import({PaymentService.class, TicketEmailService.class, QrCodeGenerator.class})
    static class TestConfig {

        @Bean
        RazorpayGateway razorpayGateway() {
            return mock(RazorpayGateway.class);
        }

        @Bean
        ResendGateway resendGateway() {
            return mock(ResendGateway.class);
        }
    }

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private RazorpayGateway razorpayGateway;

    @Autowired
    private ResendGateway resendGateway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final String customerId = "user_it_ticketflow_" + UUID.randomUUID().toString().substring(0, 8);
    private UUID eventId;
    private UUID ticketTypeId;

    /**
     * The RazorpayGateway/ResendGateway mock beans are Spring singletons
     * cached across every @Test method in this class (Spring reuses the
     * same ApplicationContext for the whole test class here) — a plain
     * {@code mock()} (unlike a MockitoExtension-managed {@code @Mock}
     * field) is never auto-reset between tests, so a stub configured in
     * one test would otherwise leak into the next. Reset explicitly here.
     */
    @BeforeEach
    void resetMocks() {
        org.mockito.Mockito.reset(razorpayGateway, resendGateway);
    }

    private void seedEventAndTicketType(int quantityTotal, BigDecimal price) {
        jdbcTemplate.update("INSERT INTO profiles (id, email, role) VALUES (?, ?, ?)",
            customerId, customerId + "@example.com", "customer");
        eventId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO events (id, organizer_id, title, event_date, event_time, venue, city, status) " +
                "VALUES (?, ?, 'Ticket Flow IT Event', CURRENT_DATE + 30, '19:00:00', 'Venue', 'City', 'approved')",
            eventId, customerId
        );
        ticketTypeId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO ticket_types (id, event_id, name, price, quantity_total, quantity_sold) VALUES (?, ?, 'General', ?, ?, 0)",
            ticketTypeId, eventId, price, quantityTotal
        );
    }

    @AfterEach
    void cleanUp() {
        if (eventId != null) {
            jdbcTemplate.update("DELETE FROM bookings WHERE event_id = ?", eventId);
            jdbcTemplate.update("DELETE FROM events WHERE id = ?", eventId);
        }
        jdbcTemplate.update("DELETE FROM profiles WHERE id = ?", customerId);
    }

    @Test
    void fullTicketLifecycle_createOrderVerifyGenerateQrPersistClaimSendEmail_againstRealDevDb() throws RazorpayException {
        seedEventAndTicketType(5, new BigDecimal("300.00"));

        when(razorpayGateway.createOrder(anyLong(), anyString(), anyString(), anyMap()))
            .thenReturn(new Order(new JSONObject().put("id", "order_it_test123")));
        when(razorpayGateway.getPublicKeyId()).thenReturn("rzp_test_it");

        CreateOrderResponse orderResponse = paymentService.createOrder(customerId,
            new CreateOrderRequest(eventId, List.of(new CreateOrderRequest.Item(ticketTypeId, 2))));

        assertThat(orderResponse.amount()).isEqualTo(60000L); // 2 * 300.00 -> paise
        UUID bookingId = orderResponse.bookingId();

        // Real dev-DB check: pending, no inventory committed yet.
        Integer soldBeforeConfirm = jdbcTemplate.queryForObject("SELECT quantity_sold FROM ticket_types WHERE id = ?", Integer.class, ticketTypeId);
        assertThat(soldBeforeConfirm).isEqualTo(0);

        when(razorpayGateway.verifySignature(anyString(), anyString(), anyString())).thenReturn(true);
        when(razorpayGateway.fetchPayment("pay_it_test123")).thenReturn(
            new com.razorpay.Payment(new JSONObject()
                .put("id", "pay_it_test123")
                .put("order_id", "order_it_test123")
                .put("status", "captured")
                .put("amount", 60000)
                .put("currency", "INR"))
        );
        when(resendGateway.sendEmail(any(), any(), any(), any(), any(), any(), any())).thenReturn("msg_it_test");

        VerifyPaymentResponse verifyResponse = paymentService.verifyPayment(customerId,
            new VerifyPaymentRequest("order_it_test123", "pay_it_test123", "sig_it_test123", bookingId));

        assertThat(verifyResponse.success()).isTrue();
        assertThat(verifyResponse.qrCode()).startsWith("data:image/png;base64,");

        // Real dev-DB checks after confirmation.
        var row = jdbcTemplate.queryForMap(
            "SELECT status, qr_code, ticket_email_sent_at FROM bookings WHERE id = ?", bookingId);
        assertThat(row.get("status")).isEqualTo("confirmed");
        assertThat((String) row.get("qr_code")).startsWith("data:image/png;base64,");
        assertThat(row.get("ticket_email_sent_at")).isNotNull();

        Integer soldAfterConfirm = jdbcTemplate.queryForObject("SELECT quantity_sold FROM ticket_types WHERE id = ?", Integer.class, ticketTypeId);
        assertThat(soldAfterConfirm).isEqualTo(2);

        // Email was actually sent (to the mocked gateway) with real gathered data.
        ArgumentCaptor<String> toCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> htmlCaptor = ArgumentCaptor.forClass(String.class);
        verify(resendGateway, times(1)).sendEmail(toCaptor.capture(), subjectCaptor.capture(), htmlCaptor.capture(),
            any(), anyString(), anyString(), anyString());
        assertThat(toCaptor.getValue()).isEqualTo(customerId + "@example.com");
        assertThat(subjectCaptor.getValue()).contains("Ticket Flow IT Event");
        assertThat(htmlCaptor.getValue()).contains("General").contains("cid:ticket-qr-code");

        // Duplicate verification: idempotent fast path, no second email.
        VerifyPaymentResponse secondVerify = paymentService.verifyPayment(customerId,
            new VerifyPaymentRequest("order_it_test123", "pay_it_test123", "sig_it_test123", bookingId));
        assertThat(secondVerify.success()).isTrue();
        assertThat(secondVerify.qrCode()).isEqualTo(verifyResponse.qrCode());
        verify(resendGateway, times(1)).sendEmail(any(), any(), any(), any(), any(), any(), any()); // still exactly once
        Integer soldAfterDuplicateVerify = jdbcTemplate.queryForObject("SELECT quantity_sold FROM ticket_types WHERE id = ?", Integer.class, ticketTypeId);
        assertThat(soldAfterDuplicateVerify).isEqualTo(2); // not double-incremented
    }

    @Test
    void emailFailure_doesNotRollBackConfirmedBookingOrPayment() throws RazorpayException {
        seedEventAndTicketType(5, new BigDecimal("100.00"));

        when(razorpayGateway.createOrder(anyLong(), anyString(), anyString(), anyMap()))
            .thenReturn(new Order(new JSONObject().put("id", "order_it_fail")));
        when(razorpayGateway.getPublicKeyId()).thenReturn("rzp_test_it");

        CreateOrderResponse orderResponse = paymentService.createOrder(customerId,
            new CreateOrderRequest(eventId, List.of(new CreateOrderRequest.Item(ticketTypeId, 1))));

        when(razorpayGateway.verifySignature(anyString(), anyString(), anyString())).thenReturn(true);
        when(razorpayGateway.fetchPayment("pay_it_fail")).thenReturn(
            new com.razorpay.Payment(new JSONObject()
                .put("id", "pay_it_fail").put("order_id", "order_it_fail")
                .put("status", "captured").put("amount", 10000).put("currency", "INR"))
        );
        when(resendGateway.sendEmail(any(), any(), any(), any(), any(), any(), any()))
            .thenThrow(new com.project926.backend.exception.ResendSendException("simulated Resend outage"));

        VerifyPaymentResponse response = paymentService.verifyPayment(customerId,
            new VerifyPaymentRequest("order_it_fail", "pay_it_fail", "sig_it_fail", orderResponse.bookingId()));

        // Payment/booking confirmation succeeded regardless of email failure.
        assertThat(response.success()).isTrue();
        String status = jdbcTemplate.queryForObject("SELECT status FROM bookings WHERE id = ?", String.class, orderResponse.bookingId());
        assertThat(status).isEqualTo("confirmed");
        String paymentStatus = jdbcTemplate.queryForObject("SELECT status FROM payments WHERE booking_id = ?", String.class, orderResponse.bookingId());
        assertThat(paymentStatus).isEqualTo("paid");
        String emailError = jdbcTemplate.queryForObject("SELECT ticket_email_error FROM bookings WHERE id = ?", String.class, orderResponse.bookingId());
        assertThat(emailError).isEqualTo("simulated Resend outage");
    }
}
