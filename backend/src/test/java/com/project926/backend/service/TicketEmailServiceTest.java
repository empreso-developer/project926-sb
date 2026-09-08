package com.project926.backend.service;

import com.project926.backend.entity.Booking;
import com.project926.backend.entity.BookingItem;
import com.project926.backend.entity.Event;
import com.project926.backend.entity.Payment;
import com.project926.backend.entity.Profile;
import com.project926.backend.entity.TicketType;
import com.project926.backend.integration.resend.ResendGateway;
import com.project926.backend.repository.BookingItemRepository;
import com.project926.backend.repository.BookingRepository;
import com.project926.backend.repository.EventRepository;
import com.project926.backend.repository.PaymentRepository;
import com.project926.backend.repository.ProfileRepository;
import com.project926.backend.repository.TicketTypeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Mirrors lib/email/send-ticket-confirmation.ts's
 * sendBookingConfirmationEmailOnce: atomic claim, data gathering, template
 * render, send via (mocked) ResendGateway, error recording. Never touches a
 * real database or the real Resend API (Step 17).
 */
@ExtendWith(MockitoExtension.class)
class TicketEmailServiceTest {

    @Mock private BookingRepository bookingRepository;
    @Mock private EventRepository eventRepository;
    @Mock private BookingItemRepository bookingItemRepository;
    @Mock private TicketTypeRepository ticketTypeRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private ProfileRepository profileRepository;
    @Mock private ResendGateway resendGateway;

    private TicketEmailService service() {
        return new TicketEmailService(bookingRepository, eventRepository, bookingItemRepository,
            ticketTypeRepository, paymentRepository, profileRepository, resendGateway, "http://localhost:3000");
    }

    private Booking booking(UUID id, String reference, UUID eventId, String customerId) {
        Booking b = newInstance(Booking.class);
        set(b, "id", id);
        set(b, "reference", reference);
        set(b, "eventId", eventId);
        set(b, "customerId", customerId);
        set(b, "totalAmount", new BigDecimal("500.00"));
        return b;
    }

    private Event event(UUID id) {
        Event e = newInstance(Event.class);
        set(e, "id", id);
        set(e, "title", "Test Concert");
        set(e, "eventDate", LocalDate.of(2026, 12, 1));
        set(e, "eventTime", LocalTime.of(19, 0));
        set(e, "venue", "Venue");
        set(e, "city", "City");
        set(e, "bannerUrl", null);
        return e;
    }

    private Profile profile(String id) {
        Profile p = newInstance(Profile.class);
        set(p, "id", id);
        set(p, "email", "customer@example.com");
        set(p, "firstName", "Jane");
        set(p, "lastName", "Doe");
        return p;
    }

    private BookingItem bookingItem(UUID bookingId, UUID ticketTypeId, int quantity) {
        BookingItem item = newInstance(BookingItem.class);
        set(item, "bookingId", bookingId);
        set(item, "ticketTypeId", ticketTypeId);
        set(item, "quantity", quantity);
        set(item, "unitPrice", new BigDecimal("250.00"));
        set(item, "subtotal", new BigDecimal("500.00"));
        return item;
    }

    private TicketType ticketType(UUID id, String name) {
        TicketType t = newInstance(TicketType.class);
        set(t, "id", id);
        set(t, "name", name);
        return t;
    }

    private Payment payment(UUID bookingId) {
        Payment p = newInstance(Payment.class);
        set(p, "bookingId", bookingId);
        set(p, "amount", new BigDecimal("500.00"));
        set(p, "currency", "INR");
        set(p, "razorpayPaymentId", "pay_abc123");
        return p;
    }

    private static final String QR_DATA_URL = "data:image/png;base64,ZmFrZS1xci1ieXRlcw==";

    @Test
    void sendBookingConfirmationEmailOnce_claimSucceeds_sendsEmailWithGatheredData() {
        UUID bookingId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        String customerId = "user_customer000000000000";
        Booking booking = booking(bookingId, "BK-1", eventId, customerId);

        when(bookingRepository.claimTicketEmailSend(eq(bookingId), any())).thenReturn(1);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event(eventId)));
        when(profileRepository.findById(customerId)).thenReturn(Optional.of(profile(customerId)));
        when(bookingItemRepository.findByBookingId(bookingId)).thenReturn(List.of(bookingItem(bookingId, ticketTypeId, 2)));
        when(ticketTypeRepository.findAllById(List.of(ticketTypeId))).thenReturn(List.of(ticketType(ticketTypeId, "General")));
        when(paymentRepository.findFirstByBookingIdOrderByCreatedAtDesc(bookingId)).thenReturn(Optional.of(payment(bookingId)));
        when(resendGateway.sendEmail(anyString(), anyString(), anyString(), any(), anyString(), anyString(), anyString()))
            .thenReturn("msg_123");

        service().sendBookingConfirmationEmailOnce(booking, QR_DATA_URL);

        ArgumentCaptor<String> toCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> htmlCaptor = ArgumentCaptor.forClass(String.class);
        verify(resendGateway).sendEmail(toCaptor.capture(), subjectCaptor.capture(), htmlCaptor.capture(),
            any(), eq("ticket-qr-code.png"), eq("image/png"), eq("ticket-qr-code"));

        assertThat(toCaptor.getValue()).isEqualTo("customer@example.com");
        assertThat(subjectCaptor.getValue()).isEqualTo("Your ticket has confirmed - Test Concert");
        assertThat(htmlCaptor.getValue()).contains("Test Concert").contains("BK-1").contains("General").contains("cid:ticket-qr-code");
        verify(bookingRepository, never()).recordTicketEmailError(any(), any());
    }

    @Test
    void sendBookingConfirmationEmailOnce_claimFails_doesNotSendEmail() {
        UUID bookingId = UUID.randomUUID();
        Booking booking = booking(bookingId, "BK-1", UUID.randomUUID(), "user_x");
        when(bookingRepository.claimTicketEmailSend(eq(bookingId), any())).thenReturn(0);

        service().sendBookingConfirmationEmailOnce(booking, QR_DATA_URL);

        verify(resendGateway, never()).sendEmail(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void sendBookingConfirmationEmailOnce_claimIsAtomic_singleConditionalUpdate_notReadThenWrite() {
        // The whole point of claimTicketEmailSend is that it's ONE
        // UPDATE...WHERE...IS NULL statement, not a separate SELECT
        // followed by an UPDATE — proven here by asserting the service
        // never calls any BookingRepository read method before the claim.
        UUID bookingId = UUID.randomUUID();
        Booking booking = booking(bookingId, "BK-1", UUID.randomUUID(), "user_x");
        when(bookingRepository.claimTicketEmailSend(eq(bookingId), any())).thenReturn(0);

        service().sendBookingConfirmationEmailOnce(booking, QR_DATA_URL);

        verify(bookingRepository, times(1)).claimTicketEmailSend(eq(bookingId), any());
        verify(bookingRepository, never()).findById(any());
    }

    @Test
    void sendBookingConfirmationEmailOnce_resendFailure_recordsErrorOnBooking_neverThrows() {
        UUID bookingId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        String customerId = "user_customer000000000000";
        Booking booking = booking(bookingId, "BK-1", eventId, customerId);

        when(bookingRepository.claimTicketEmailSend(eq(bookingId), any())).thenReturn(1);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event(eventId)));
        when(profileRepository.findById(customerId)).thenReturn(Optional.of(profile(customerId)));
        when(bookingItemRepository.findByBookingId(bookingId)).thenReturn(List.of());
        when(paymentRepository.findFirstByBookingIdOrderByCreatedAtDesc(bookingId)).thenReturn(Optional.empty());
        when(resendGateway.sendEmail(any(), any(), any(), any(), any(), any(), any()))
            .thenThrow(new com.project926.backend.exception.ResendSendException("Resend API error"));

        // Must not throw — this is the never-fails contract PaymentService relies on.
        service().sendBookingConfirmationEmailOnce(booking, QR_DATA_URL);

        ArgumentCaptor<String> errorCaptor = ArgumentCaptor.forClass(String.class);
        verify(bookingRepository).recordTicketEmailError(eq(bookingId), errorCaptor.capture());
        assertThat(errorCaptor.getValue()).isEqualTo("Resend API error");
    }

    @Test
    void sendBookingConfirmationEmailOnce_errorMessageTruncatedTo500Chars() {
        UUID bookingId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        String customerId = "user_customer000000000000";
        Booking booking = booking(bookingId, "BK-1", eventId, customerId);
        String longMessage = "x".repeat(600);

        when(bookingRepository.claimTicketEmailSend(eq(bookingId), any())).thenReturn(1);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event(eventId)));
        when(profileRepository.findById(customerId)).thenReturn(Optional.of(profile(customerId)));
        when(bookingItemRepository.findByBookingId(bookingId)).thenReturn(List.of());
        when(paymentRepository.findFirstByBookingIdOrderByCreatedAtDesc(bookingId)).thenReturn(Optional.empty());
        when(resendGateway.sendEmail(any(), any(), any(), any(), any(), any(), any()))
            .thenThrow(new RuntimeException(longMessage));

        service().sendBookingConfirmationEmailOnce(booking, QR_DATA_URL);

        ArgumentCaptor<String> errorCaptor = ArgumentCaptor.forClass(String.class);
        verify(bookingRepository).recordTicketEmailError(eq(bookingId), errorCaptor.capture());
        assertThat(errorCaptor.getValue()).hasSize(500);
    }

    @Test
    void sendBookingConfirmationEmailOnce_missingEventData_recordsErrorWithoutThrowing() {
        UUID bookingId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        String customerId = "user_customer000000000000";
        Booking booking = booking(bookingId, "BK-1", eventId, customerId);

        when(bookingRepository.claimTicketEmailSend(eq(bookingId), any())).thenReturn(1);
        when(eventRepository.findById(eventId)).thenReturn(Optional.empty()); // missing event
        when(profileRepository.findById(customerId)).thenReturn(Optional.of(profile(customerId)));

        service().sendBookingConfirmationEmailOnce(booking, QR_DATA_URL);

        verify(bookingRepository).recordTicketEmailError(eq(bookingId), anyString());
        verify(resendGateway, never()).sendEmail(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void sendBookingConfirmationEmailOnce_extractsRawBytesFromDataUrl_notTheDataUrlItself() {
        UUID bookingId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        String customerId = "user_customer000000000000";
        Booking booking = booking(bookingId, "BK-1", eventId, customerId);

        when(bookingRepository.claimTicketEmailSend(eq(bookingId), any())).thenReturn(1);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event(eventId)));
        when(profileRepository.findById(customerId)).thenReturn(Optional.of(profile(customerId)));
        when(bookingItemRepository.findByBookingId(bookingId)).thenReturn(List.of());
        when(paymentRepository.findFirstByBookingIdOrderByCreatedAtDesc(bookingId)).thenReturn(Optional.empty());
        when(resendGateway.sendEmail(any(), any(), any(), any(), any(), any(), any())).thenReturn("msg_x");

        service().sendBookingConfirmationEmailOnce(booking, QR_DATA_URL);

        ArgumentCaptor<byte[]> bytesCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(resendGateway).sendEmail(any(), any(), any(), bytesCaptor.capture(), any(), any(), any());
        assertThat(new String(bytesCaptor.getValue())).isEqualTo("fake-qr-bytes");
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
