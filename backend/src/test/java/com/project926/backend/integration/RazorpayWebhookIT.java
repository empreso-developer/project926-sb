package com.project926.backend.integration;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.project926.backend.dto.CreateOrderRequest;
import com.project926.backend.dto.CreateOrderResponse;
import com.project926.backend.dto.RazorpayPaymentEntity;
import com.project926.backend.dto.VerifyPaymentRequest;
import com.project926.backend.entity.Profile;
import com.project926.backend.integration.qrcode.QrCodeGenerator;
import com.project926.backend.integration.razorpay.RazorpayGateway;
import com.project926.backend.integration.resend.ResendGateway;
import com.project926.backend.repository.ProfileRepository;
import com.project926.backend.service.PaymentService;
import com.project926.backend.service.RazorpayWebhookService;
import com.razorpay.Order;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
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

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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
 * Phase G's mandatory real-database proof for the Razorpay webhook: full
 * lifecycle, duplicate-delivery idempotency, wrong-correlation handling,
 * SOLD_OUT behavior, and the webhook/verify race — all against the real
 * DEVELOPMENT Supabase database (memdvuuszsistdjckcfp — never production).
 * Only RazorpayGateway and ResendGateway are mocked (never real Razorpay/
 * Resend calls, per Step 21/34); every database operation, including both
 * existing RPCs and the real confirmCapturedPayment/RazorpayWebhookService
 * code paths, is real.
 *
 * All rows created here are deleted in @AfterEach with explicit residue
 * verification, matching the established Phase D/E/F pattern.
 */
@SpringBootTest(
    classes = RazorpayWebhookIT.TestConfig.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@ActiveProfiles("dev")
@EnabledIfEnvironmentVariable(named = "SUPABASE_DB_PASSWORD", matches = ".+")
class RazorpayWebhookIT {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = Profile.class)
    @EnableJpaRepositories(basePackageClasses = ProfileRepository.class)
    @ComponentScan(basePackages = "com.project926.backend.repository")
    @Import({PaymentService.class, RazorpayWebhookService.class, com.project926.backend.service.TicketEmailService.class, QrCodeGenerator.class})
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
    private RazorpayWebhookService webhookService;

    @Autowired
    private RazorpayGateway razorpayGateway;

    @Autowired
    private ResendGateway resendGateway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final String customerId = "user_it_rzpwh_" + UUID.randomUUID().toString().substring(0, 8);
    private UUID eventId;
    private UUID ticketTypeId;

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
                "VALUES (?, ?, 'Webhook IT Event', CURRENT_DATE + 30, '19:00:00', 'Venue', 'City', 'approved')",
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

        Integer remainingBookings = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM bookings WHERE customer_id = ?", Integer.class, customerId);
        Integer remainingProfiles = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM profiles WHERE id = ?", Integer.class, customerId);
        assertThat(remainingBookings).isZero();
        assertThat(remainingProfiles).isZero();
    }

    private UUID createPendingOrder(String razorpayOrderId, int quantity, BigDecimal unitPrice) throws Exception {
        when(razorpayGateway.createOrder(anyLong(), anyString(), anyString(), anyMap()))
            .thenReturn(new Order(new JSONObject().put("id", razorpayOrderId)));
        when(razorpayGateway.getPublicKeyId()).thenReturn("rzp_test_it");

        CreateOrderResponse response = paymentService.createOrder(customerId,
            new CreateOrderRequest(eventId, List.of(new CreateOrderRequest.Item(ticketTypeId, quantity))));
        return response.bookingId();
    }

    private long expectedPaise(int quantity, BigDecimal unitPrice) {
        return unitPrice.multiply(BigDecimal.valueOf(quantity)).multiply(BigDecimal.valueOf(100)).longValueExact();
    }

    private static String decodeQr(String dataUrl) throws Exception {
        String base64 = dataUrl.substring(dataUrl.indexOf(',') + 1);
        byte[] pngBytes = Base64.getDecoder().decode(base64);
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(pngBytes));
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image)));
        Map<DecodeHintType, Object> hints = Map.of(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        Result result = new MultiFormatReader().decode(bitmap, hints);
        return result.getText();
    }

    // ---- 1. successful webhook lifecycle + QR verification -------------------

    @Test
    void capturedWebhook_confirmsBookingCommitsInventoryPersistsValidQrAndSendsEmail() throws Exception {
        seedEventAndTicketType(5, new BigDecimal("300.00"));
        UUID bookingId = createPendingOrder("order_it_wh1", 2, new BigDecimal("300.00"));
        when(resendGateway.sendEmail(any(), any(), any(), any(), any(), any(), any())).thenReturn("msg_it_wh1");

        webhookService.process("payment.captured",
            new RazorpayPaymentEntity("pay_it_wh1", "order_it_wh1", expectedPaise(2, new BigDecimal("300.00")), "INR", "captured"));

        var row = jdbcTemplate.queryForMap(
            "SELECT status, qr_code, reference FROM bookings WHERE id = ?", bookingId);
        assertThat(row.get("status")).isEqualTo("confirmed");
        String qrCode = (String) row.get("qr_code");
        assertThat(qrCode).startsWith("data:image/png;base64,");

        JSONObject decoded = new JSONObject(decodeQr(qrCode));
        assertThat(decoded.getString("ref")).isEqualTo(row.get("reference"));
        assertThat(decoded.getString("booking_id")).isEqualTo(bookingId.toString());
        assertThat(decoded.getString("event_id")).isEqualTo(eventId.toString());

        Integer soldAfter = jdbcTemplate.queryForObject("SELECT quantity_sold FROM ticket_types WHERE id = ?", Integer.class, ticketTypeId);
        assertThat(soldAfter).isEqualTo(2);

        var paymentRow = jdbcTemplate.queryForMap(
            "SELECT status, razorpay_payment_id FROM payments WHERE booking_id = ?", bookingId);
        assertThat(paymentRow.get("status")).isEqualTo("paid");
        assertThat(paymentRow.get("razorpay_payment_id")).isEqualTo("pay_it_wh1");

        Object emailSentAt = jdbcTemplate.queryForObject("SELECT ticket_email_sent_at FROM bookings WHERE id = ?", Object.class, bookingId);
        assertThat(emailSentAt).isNotNull();
        verify(resendGateway, times(1)).sendEmail(any(), any(), any(), any(), any(), any(), any());
    }

    // ---- 2. duplicate webhook idempotency -------------------------------------

    @Test
    void duplicateCapturedWebhook_doesNotDoubleIncrementInventoryOrResendEmail() throws Exception {
        seedEventAndTicketType(5, new BigDecimal("150.00"));
        UUID bookingId = createPendingOrder("order_it_wh2", 1, new BigDecimal("150.00"));
        when(resendGateway.sendEmail(any(), any(), any(), any(), any(), any(), any())).thenReturn("msg_it_wh2");
        RazorpayPaymentEntity entity = new RazorpayPaymentEntity("pay_it_wh2", "order_it_wh2", expectedPaise(1, new BigDecimal("150.00")), "INR", "captured");

        webhookService.process("payment.captured", entity);
        Object firstEmailSentAt = jdbcTemplate.queryForObject("SELECT ticket_email_sent_at FROM bookings WHERE id = ?", Object.class, bookingId);
        String firstQr = jdbcTemplate.queryForObject("SELECT qr_code FROM bookings WHERE id = ?", String.class, bookingId);

        // Deliver the exact same event 9 more times (10 total, per Section 34's spirit).
        for (int i = 0; i < 9; i++) {
            webhookService.process("payment.captured", entity);
        }

        Integer soldAfter = jdbcTemplate.queryForObject("SELECT quantity_sold FROM ticket_types WHERE id = ?", Integer.class, ticketTypeId);
        assertThat(soldAfter).isEqualTo(1); // never double-incremented

        Object finalEmailSentAt = jdbcTemplate.queryForObject("SELECT ticket_email_sent_at FROM bookings WHERE id = ?", Object.class, bookingId);
        assertThat(finalEmailSentAt).isEqualTo(firstEmailSentAt); // not rewritten

        String finalQr = jdbcTemplate.queryForObject("SELECT qr_code FROM bookings WHERE id = ?", String.class, bookingId);
        assertThat(finalQr).isEqualTo(firstQr); // not overwritten with a different (though logically identical) QR

        verify(resendGateway, times(1)).sendEmail(any(), any(), any(), any(), any(), any(), any()); // exactly once total
    }

    // ---- 3. wrong payment/order correlation -----------------------------------

    @Test
    void webhookForUnknownRazorpayOrder_isHandledSafely_touchesNothing() {
        seedEventAndTicketType(5, new BigDecimal("100.00"));

        webhookService.process("payment.captured",
            new RazorpayPaymentEntity("pay_unknown", "order_completely_unknown_id", 10000L, "INR", "captured"));

        Integer soldAfter = jdbcTemplate.queryForObject("SELECT quantity_sold FROM ticket_types WHERE id = ?", Integer.class, ticketTypeId);
        assertThat(soldAfter).isEqualTo(0);
        verify(resendGateway, never()).sendEmail(any(), any(), any(), any(), any(), any(), any());
    }

    // ---- 4. SOLD_OUT via webhook -----------------------------------------------

    @Test
    void soldOutDuringWebhookConfirmation_marksPaymentPaidCancelsBooking_neverOversellsOrEmails() throws Exception {
        seedEventAndTicketType(1, new BigDecimal("100.00"));
        UUID bookingA = createPendingOrder("order_it_wh_soldout_a", 1, new BigDecimal("100.00"));
        // Backdate A's hold so B can reserve the same single slot — same
        // technique BookingInventoryIT uses to reproduce the exact
        // "hold lapsed, someone else's confirmed purchase took the seat"
        // race documented in the inventory-hold-race migration.
        jdbcTemplate.update("UPDATE bookings SET expires_at = now() - interval '1 hour' WHERE id = ?", bookingA);
        UUID bookingB = createPendingOrder("order_it_wh_soldout_b", 1, new BigDecimal("100.00"));
        when(resendGateway.sendEmail(any(), any(), any(), any(), any(), any(), any())).thenReturn("msg_it_soldout");

        webhookService.process("payment.captured",
            new RazorpayPaymentEntity("pay_it_soldout_a", "order_it_wh_soldout_a", expectedPaise(1, new BigDecimal("100.00")), "INR", "captured"));
        webhookService.process("payment.captured",
            new RazorpayPaymentEntity("pay_it_soldout_b", "order_it_wh_soldout_b", expectedPaise(1, new BigDecimal("100.00")), "INR", "captured"));

        Integer soldAfter = jdbcTemplate.queryForObject("SELECT quantity_sold FROM ticket_types WHERE id = ?", Integer.class, ticketTypeId);
        assertThat(soldAfter).isEqualTo(1); // never exceeds capacity

        String statusA = jdbcTemplate.queryForObject("SELECT status FROM bookings WHERE id = ?", String.class, bookingA);
        String statusB = jdbcTemplate.queryForObject("SELECT status FROM bookings WHERE id = ?", String.class, bookingB);
        assertThat(statusA).isEqualTo("confirmed");
        assertThat(statusB).isEqualTo("cancelled"); // captured but unfulfillable -> cancelled, per SOLD_OUT precedent

        var paymentBRow = jdbcTemplate.queryForMap("SELECT status, razorpay_payment_id FROM payments WHERE booking_id = ?", bookingB);
        assertThat(paymentBRow.get("status")).isEqualTo("paid"); // money real -> marked paid despite cancellation, for manual refund
        assertThat(paymentBRow.get("razorpay_payment_id")).isEqualTo("pay_it_soldout_b");

        Object emailSentAtB = jdbcTemplate.queryForObject("SELECT ticket_email_sent_at FROM bookings WHERE id = ?", Object.class, bookingB);
        assertThat(emailSentAtB).isNull(); // no ticket email for an unfulfilled booking
        verify(resendGateway, times(1)).sendEmail(any(), any(), any(), any(), any(), any(), any()); // only A's email
    }

    // ---- 5. webhook + verify race -----------------------------------------------

    @Test
    void webhookAndBrowserVerify_racingForTheSameBooking_resultInExactlyOneConfirmation() throws Exception {
        seedEventAndTicketType(3, new BigDecimal("250.00"));
        UUID bookingId = createPendingOrder("order_it_race", 1, new BigDecimal("250.00"));

        when(razorpayGateway.verifySignature(anyString(), anyString(), anyString())).thenReturn(true);
        when(razorpayGateway.fetchPayment("pay_it_race")).thenReturn(
            new com.razorpay.Payment(new JSONObject()
                .put("id", "pay_it_race")
                .put("order_id", "order_it_race")
                .put("status", "captured")
                .put("amount", expectedPaise(1, new BigDecimal("250.00")))
                .put("currency", "INR"))
        );
        when(resendGateway.sendEmail(any(), any(), any(), any(), any(), any(), any())).thenReturn("msg_it_race");

        CountDownLatch startLine = new CountDownLatch(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<Void> verifyTask = () -> {
                startLine.countDown();
                startLine.await(10, TimeUnit.SECONDS);
                paymentService.verifyPayment(customerId,
                    new VerifyPaymentRequest("order_it_race", "pay_it_race", "sig_it_race", bookingId));
                return null;
            };
            java.util.concurrent.Callable<Void> webhookTask = () -> {
                startLine.countDown();
                startLine.await(10, TimeUnit.SECONDS);
                webhookService.process("payment.captured",
                    new RazorpayPaymentEntity("pay_it_race", "order_it_race", expectedPaise(1, new BigDecimal("250.00")), "INR", "captured"));
                return null;
            };
            java.util.concurrent.Future<Void> verifyFuture = pool.submit(verifyTask);
            java.util.concurrent.Future<Void> webhookFuture = pool.submit(webhookTask);
            verifyFuture.get(30, TimeUnit.SECONDS);
            webhookFuture.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdown();
        }

        Integer soldAfter = jdbcTemplate.queryForObject("SELECT quantity_sold FROM ticket_types WHERE id = ?", Integer.class, ticketTypeId);
        assertThat(soldAfter).isEqualTo(1); // exactly one commit, not two

        var row = jdbcTemplate.queryForMap("SELECT status, qr_code FROM bookings WHERE id = ?", bookingId);
        assertThat(row.get("status")).isEqualTo("confirmed");
        String qrCode = (String) row.get("qr_code");
        JSONObject decoded = new JSONObject(decodeQr(qrCode));
        assertThat(decoded.getString("booking_id")).isEqualTo(bookingId.toString());

        var paymentRow = jdbcTemplate.queryForMap("SELECT status FROM payments WHERE booking_id = ?", bookingId);
        assertThat(paymentRow.get("status")).isEqualTo("paid");

        verify(resendGateway, times(1)).sendEmail(any(), any(), any(), any(), any(), any(), any()); // exactly once
    }
}
