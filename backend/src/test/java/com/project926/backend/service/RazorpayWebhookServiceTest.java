package com.project926.backend.service;

import com.project926.backend.dto.RazorpayPaymentEntity;
import com.project926.backend.dto.VerifyPaymentResponse;
import com.project926.backend.entity.Booking;
import com.project926.backend.entity.Payment;
import com.project926.backend.exception.BookingConfirmationException;
import com.project926.backend.exception.SoldOutException;
import com.project926.backend.repository.BookingRepository;
import com.project926.backend.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Mocked-repository coverage of RazorpayWebhookService's event-handling
 * matrix (Step 31/32 of the Phase G brief). PaymentService itself is
 * mocked here — its own confirmCapturedPayment behavior is already fully
 * covered by PaymentServiceTest (Phase D), so this class tests only
 * RazorpayWebhookService's own decisions: correlation, event-state
 * cross-checks, and which outcomes call into confirmCapturedPayment at
 * all. Real end-to-end confirmation (webhook -> real RPC -> real DB) is
 * RazorpayWebhookIT's job.
 */
@ExtendWith(MockitoExtension.class)
class RazorpayWebhookServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private PaymentService paymentService;

    private RazorpayWebhookService service() {
        return new RazorpayWebhookService(paymentRepository, bookingRepository, paymentService);
    }

    private static final String ORDER_ID = "order_test123";
    private static final String RZP_PAYMENT_ID = "pay_test123";

    private Payment payment(UUID id, UUID bookingId, String orderId, BigDecimal amount, String currency, String status) {
        Payment p = newInstance(Payment.class);
        set(p, "id", id);
        set(p, "bookingId", bookingId);
        set(p, "razorpayOrderId", orderId);
        set(p, "amount", amount);
        set(p, "currency", currency);
        set(p, "status", status);
        return p;
    }

    private Booking booking(UUID id, String status, String reference) {
        Booking b = newInstance(Booking.class);
        set(b, "id", id);
        set(b, "status", status);
        set(b, "reference", reference);
        return b;
    }

    private RazorpayPaymentEntity capturedEntity(long amountPaise, String currency) {
        return new RazorpayPaymentEntity(RZP_PAYMENT_ID, ORDER_ID, amountPaise, currency, "captured");
    }

    private RazorpayPaymentEntity failedEntity() {
        return new RazorpayPaymentEntity(RZP_PAYMENT_ID, ORDER_ID, 50000L, "INR", "failed");
    }

    // ---- correlation / unsupported events ---------------------------------

    @Test
    void process_unsupportedEventType_isIgnored_noRepositoryLookup() {
        service().process("order.paid", capturedEntity(50000, "INR"));

        verify(paymentRepository, never()).findByRazorpayOrderId(any());
    }

    @Test
    void process_noMatchingPayment_isIgnoredSafely() {
        when(paymentRepository.findByRazorpayOrderId(ORDER_ID)).thenReturn(Optional.empty());

        assertThatCode(() -> service().process("payment.captured", capturedEntity(50000, "INR")))
            .doesNotThrowAnyException();
        verify(paymentService, never()).confirmCapturedPayment(any(), any(), any(), any());
    }

    @Test
    void process_paymentWithNoMatchingBooking_isIgnoredSafely() {
        UUID paymentId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        when(paymentRepository.findByRazorpayOrderId(ORDER_ID))
            .thenReturn(Optional.of(payment(paymentId, bookingId, ORDER_ID, new BigDecimal("500.00"), "INR", "created")));
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.empty());

        assertThatCode(() -> service().process("payment.captured", capturedEntity(50000, "INR")))
            .doesNotThrowAnyException();
        verify(paymentService, never()).confirmCapturedPayment(any(), any(), any(), any());
    }

    // ---- payment.captured ---------------------------------------------------

    @Test
    void captured_pendingBooking_confirmsViaSharedPaymentService() {
        UUID paymentId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        Payment payment = payment(paymentId, bookingId, ORDER_ID, new BigDecimal("500.00"), "INR", "created");
        Booking booking = booking(bookingId, "pending", "BK-1");
        when(paymentRepository.findByRazorpayOrderId(ORDER_ID)).thenReturn(Optional.of(payment));
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(paymentService.confirmCapturedPayment(booking, payment, RZP_PAYMENT_ID, null))
            .thenReturn(new VerifyPaymentResponse(true, bookingId, "BK-1", "data:image/png;base64,xxx"));

        service().process("payment.captured", capturedEntity(50000, "INR"));

        verify(paymentService).confirmCapturedPayment(booking, payment, RZP_PAYMENT_ID, null);
    }

    @Test
    void captured_alreadyConfirmedBooking_isIdempotent_neverCallsConfirm() {
        UUID paymentId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        Payment payment = payment(paymentId, bookingId, ORDER_ID, new BigDecimal("500.00"), "INR", "paid");
        Booking booking = booking(bookingId, "confirmed", "BK-1");
        when(paymentRepository.findByRazorpayOrderId(ORDER_ID)).thenReturn(Optional.of(payment));
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        service().process("payment.captured", capturedEntity(50000, "INR"));
        service().process("payment.captured", capturedEntity(50000, "INR")); // duplicate delivery

        verify(paymentService, never()).confirmCapturedPayment(any(), any(), any(), any());
    }

    @Test
    void captured_cancelledBooking_unpaidPayment_marksPaidForManualRefund_doesNotResurrectBooking() {
        UUID paymentId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        Payment payment = payment(paymentId, bookingId, ORDER_ID, new BigDecimal("500.00"), "INR", "failed");
        Booking booking = booking(bookingId, "cancelled", "BK-1");
        when(paymentRepository.findByRazorpayOrderId(ORDER_ID)).thenReturn(Optional.of(payment));
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        service().process("payment.captured", capturedEntity(50000, "INR"));

        verify(paymentRepository).markPaid(paymentId, RZP_PAYMENT_ID, null, "paid");
        verify(paymentService, never()).confirmCapturedPayment(any(), any(), any(), any());
        verify(bookingRepository, never()).updateStatus(any(), any());
    }

    @Test
    void captured_cancelledBooking_alreadyPaidPayment_isIdempotent_noDuplicateMarkPaid() {
        UUID paymentId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        Payment payment = payment(paymentId, bookingId, ORDER_ID, new BigDecimal("500.00"), "INR", "paid");
        Booking booking = booking(bookingId, "cancelled", "BK-1");
        when(paymentRepository.findByRazorpayOrderId(ORDER_ID)).thenReturn(Optional.of(payment));
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        service().process("payment.captured", capturedEntity(50000, "INR"));

        verify(paymentRepository, never()).markPaid(any(), any(), any(), any());
    }

    @Test
    void captured_amountMismatch_doesNotConfirm() {
        UUID paymentId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        Payment payment = payment(paymentId, bookingId, ORDER_ID, new BigDecimal("500.00"), "INR", "created");
        Booking booking = booking(bookingId, "pending", "BK-1");
        when(paymentRepository.findByRazorpayOrderId(ORDER_ID)).thenReturn(Optional.of(payment));
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        service().process("payment.captured", capturedEntity(40000, "INR")); // expected 50000

        verify(paymentService, never()).confirmCapturedPayment(any(), any(), any(), any());
    }

    @Test
    void captured_currencyMismatch_doesNotConfirm() {
        UUID paymentId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        Payment payment = payment(paymentId, bookingId, ORDER_ID, new BigDecimal("500.00"), "INR", "created");
        Booking booking = booking(bookingId, "pending", "BK-1");
        when(paymentRepository.findByRazorpayOrderId(ORDER_ID)).thenReturn(Optional.of(payment));
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        service().process("payment.captured", capturedEntity(50000, "USD"));

        verify(paymentService, never()).confirmCapturedPayment(any(), any(), any(), any());
    }

    @Test
    void captured_inconsistentEntityStatus_doesNotConfirm() {
        // event says payment.captured but the entity's own status field
        // disagrees -- do not act on an internally inconsistent payload.
        UUID paymentId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        Payment payment = payment(paymentId, bookingId, ORDER_ID, new BigDecimal("500.00"), "INR", "created");
        Booking booking = booking(bookingId, "pending", "BK-1");
        when(paymentRepository.findByRazorpayOrderId(ORDER_ID)).thenReturn(Optional.of(payment));
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        RazorpayPaymentEntity inconsistent = new RazorpayPaymentEntity(RZP_PAYMENT_ID, ORDER_ID, 50000L, "INR", "authorized");

        service().process("payment.captured", inconsistent);

        verify(paymentService, never()).confirmCapturedPayment(any(), any(), any(), any());
    }

    @Test
    void captured_soldOutFromSharedConfirmation_isSwallowed_notPropagated() {
        UUID paymentId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        Payment payment = payment(paymentId, bookingId, ORDER_ID, new BigDecimal("500.00"), "INR", "created");
        Booking booking = booking(bookingId, "pending", "BK-1");
        when(paymentRepository.findByRazorpayOrderId(ORDER_ID)).thenReturn(Optional.of(payment));
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(paymentService.confirmCapturedPayment(booking, payment, RZP_PAYMENT_ID, null))
            .thenThrow(new SoldOutException("SOLD_OUT: not enough tickets remaining"));

        // Fully handled internally (side effects already happened inside
        // confirmCapturedPayment) -- must NOT propagate to the controller
        // as a 500, since retrying achieves nothing.
        assertThatCode(() -> service().process("payment.captured", capturedEntity(50000, "INR")))
            .doesNotThrowAnyException();
    }

    @Test
    void captured_bookingConfirmationException_propagates_forControllerToTreatAsRetryable() {
        UUID paymentId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        Payment payment = payment(paymentId, bookingId, ORDER_ID, new BigDecimal("500.00"), "INR", "created");
        Booking booking = booking(bookingId, "pending", "BK-1");
        when(paymentRepository.findByRazorpayOrderId(ORDER_ID)).thenReturn(Optional.of(payment));
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(paymentService.confirmCapturedPayment(booking, payment, RZP_PAYMENT_ID, null))
            .thenThrow(new BookingConfirmationException("unexpected RPC failure"));

        assertThatThrownBy(() -> service().process("payment.captured", capturedEntity(50000, "INR")))
            .isInstanceOf(BookingConfirmationException.class);
    }

    // ---- payment.failed -----------------------------------------------------

    @Test
    void failed_createdPayment_marksFailed_doesNotTouchBooking() {
        UUID paymentId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        Payment payment = payment(paymentId, bookingId, ORDER_ID, new BigDecimal("500.00"), "INR", "created");
        Booking booking = booking(bookingId, "pending", "BK-1");
        when(paymentRepository.findByRazorpayOrderId(ORDER_ID)).thenReturn(Optional.of(payment));
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        service().process("payment.failed", failedEntity());

        verify(paymentRepository).updateStatus(paymentId, "failed");
        verify(bookingRepository, never()).updateStatus(any(), any());
    }

    @Test
    void failed_alreadyPaidPayment_isIgnored_neverOverwritesSuccess() {
        UUID paymentId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        Payment payment = payment(paymentId, bookingId, ORDER_ID, new BigDecimal("500.00"), "INR", "paid");
        Booking booking = booking(bookingId, "confirmed", "BK-1");
        when(paymentRepository.findByRazorpayOrderId(ORDER_ID)).thenReturn(Optional.of(payment));
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        service().process("payment.failed", failedEntity());

        verify(paymentRepository, never()).updateStatus(any(), any());
    }

    @Test
    void failed_alreadyFailedPayment_isIdempotent() {
        UUID paymentId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        Payment payment = payment(paymentId, bookingId, ORDER_ID, new BigDecimal("500.00"), "INR", "failed");
        Booking booking = booking(bookingId, "pending", "BK-1");
        when(paymentRepository.findByRazorpayOrderId(ORDER_ID)).thenReturn(Optional.of(payment));
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        service().process("payment.failed", failedEntity());

        verify(paymentRepository, never()).updateStatus(any(), any());
    }

    @Test
    void failed_nonexistentPayment_isHandledSafely() {
        when(paymentRepository.findByRazorpayOrderId(ORDER_ID)).thenReturn(Optional.empty());

        assertThatCode(() -> service().process("payment.failed", failedEntity()))
            .doesNotThrowAnyException();
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
