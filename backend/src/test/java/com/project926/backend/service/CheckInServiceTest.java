package com.project926.backend.service;

import com.project926.backend.dto.CheckInResponse;
import com.project926.backend.entity.Booking;
import com.project926.backend.entity.BookingItem;
import com.project926.backend.entity.TicketType;
import com.project926.backend.repository.BookingItemRepository;
import com.project926.backend.repository.BookingRepository;
import com.project926.backend.repository.ProfileRepository;
import com.project926.backend.repository.TicketTypeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.math.BigDecimal;
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
 * Mocked-repository coverage of CheckInService's business-outcome switch —
 * mirrors app/(project926)/project926/api/organizer/events/[eventId]/check-in/route.ts's
 * status branches exactly (Step 12's check-in test matrix). The
 * database-level atomicity guarantee itself (the guarded UPDATE actually
 * being race-safe across concurrent transactions) is NOT provable with
 * mocks — that is CheckInConcurrencyIT's job, against the real dev DB.
 */
@ExtendWith(MockitoExtension.class)
class CheckInServiceTest {

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private BookingItemRepository bookingItemRepository;
    @Mock
    private TicketTypeRepository ticketTypeRepository;
    @Mock
    private ProfileRepository profileRepository;

    private CheckInService service() {
        return new CheckInService(bookingRepository, bookingItemRepository, ticketTypeRepository, profileRepository);
    }

    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final String ORGANIZER_ID = "user_organizer00000000000";

    private Booking booking(UUID id, UUID eventId, String status, OffsetDateTime checkedInAt) {
        Booking b = newInstance(Booking.class);
        set(b, "id", id);
        set(b, "reference", "REF-" + id.toString().substring(0, 8));
        set(b, "customerId", "user_customer000000000000");
        set(b, "eventId", eventId);
        set(b, "status", status);
        set(b, "totalAmount", new BigDecimal("100.00"));
        set(b, "checkedInAt", checkedInAt);
        set(b, "checkedInBy", checkedInAt != null ? ORGANIZER_ID : null);
        set(b, "createdAt", OffsetDateTime.now());
        set(b, "updatedAt", OffsetDateTime.now());
        return b;
    }

    private void stubEmptyAttendeeLookups() {
        when(profileRepository.findById(anyString())).thenReturn(Optional.empty());
        when(bookingItemRepository.findByBookingId(any())).thenReturn(List.of());
    }

    @Test
    void checkIn_bookingNotFoundById_returnsInvalid() {
        CheckInService service = service();
        UUID bookingId = UUID.randomUUID();
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.empty());

        CheckInResponse response = service.checkIn(EVENT_ID, ORGANIZER_ID, bookingId, null);

        assertThat(response.success()).isFalse();
        assertThat(response.status()).isEqualTo("invalid");
        verify(bookingRepository, never()).checkIn(any(), any(), any(), any());
    }

    @Test
    void checkIn_bookingNotFoundByReference_returnsInvalid() {
        CheckInService service = service();
        when(bookingRepository.findByReference("BOGUS")).thenReturn(Optional.empty());

        CheckInResponse response = service.checkIn(EVENT_ID, ORGANIZER_ID, null, "BOGUS");

        assertThat(response.status()).isEqualTo("invalid");
    }

    @Test
    void checkIn_bookingBelongsToDifferentEvent_returnsWrongEvent_andNeverIssuesUpdate() {
        CheckInService service = service();
        UUID bookingId = UUID.randomUUID();
        UUID otherEventId = UUID.randomUUID();
        Booking booking = booking(bookingId, otherEventId, "confirmed", null);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        CheckInResponse response = service.checkIn(EVENT_ID, ORGANIZER_ID, bookingId, null);

        assertThat(response.success()).isFalse();
        assertThat(response.status()).isEqualTo("wrong_event");
        // The critical wrong-event protection: no guarded UPDATE is even
        // attempted for the URL's eventId once the Java-side check already
        // caught the mismatch — CheckInConcurrencyIT/CheckInWrongEventIT
        // prove the DB-level guard independently.
        verify(bookingRepository, never()).checkIn(any(), any(), any(), any());
    }

    @Test
    void checkIn_pendingBooking_returnsPaymentNotConfirmed() {
        CheckInService service = service();
        UUID bookingId = UUID.randomUUID();
        Booking booking = booking(bookingId, EVENT_ID, "pending", null);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        CheckInResponse response = service.checkIn(EVENT_ID, ORGANIZER_ID, bookingId, null);

        assertThat(response.status()).isEqualTo("payment_not_confirmed");
        verify(bookingRepository, never()).checkIn(any(), any(), any(), any());
    }

    @Test
    void checkIn_cancelledBooking_returnsCancelled() {
        CheckInService service = service();
        UUID bookingId = UUID.randomUUID();
        Booking booking = booking(bookingId, EVENT_ID, "cancelled", null);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        CheckInResponse response = service.checkIn(EVENT_ID, ORGANIZER_ID, bookingId, null);

        assertThat(response.status()).isEqualTo("cancelled");
    }

    @Test
    void checkIn_alreadyCheckedInAccordingToLoadedRow_returnsAlreadyCheckedIn_withoutAttemptingUpdate() {
        CheckInService service = service();
        UUID bookingId = UUID.randomUUID();
        OffsetDateTime checkedInAt = OffsetDateTime.now().minusMinutes(5);
        Booking booking = booking(bookingId, EVENT_ID, "confirmed", checkedInAt);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        stubEmptyAttendeeLookups();

        CheckInResponse response = service.checkIn(EVENT_ID, ORGANIZER_ID, bookingId, null);

        assertThat(response.success()).isFalse();
        assertThat(response.status()).isEqualTo("already_checked_in");
        assertThat(response.checkedInAt()).isEqualTo(checkedInAt);
        verify(bookingRepository, never()).checkIn(any(), any(), any(), any());
    }

    @Test
    void checkIn_confirmedNotYetCheckedIn_winsGuardedUpdate_returnsCheckedIn() {
        CheckInService service = service();
        UUID bookingId = UUID.randomUUID();
        Booking booking = booking(bookingId, EVENT_ID, "confirmed", null);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(bookingRepository.checkIn(eq(bookingId), eq(EVENT_ID), any(), eq(ORGANIZER_ID))).thenReturn(1);
        stubEmptyAttendeeLookups();

        CheckInResponse response = service.checkIn(EVENT_ID, ORGANIZER_ID, bookingId, null);

        assertThat(response.success()).isTrue();
        assertThat(response.status()).isEqualTo("checked_in");
        assertThat(response.booking().reference()).isEqualTo(booking.getReference());
        verify(bookingRepository, times(1)).checkIn(eq(bookingId), eq(EVENT_ID), any(), eq(ORGANIZER_ID));
    }

    @Test
    void checkIn_losesGuardedUpdateRace_fallsBackToAlreadyCheckedIn() {
        // Simulates the concurrent-request scenario: the Java-side read
        // still saw checkedInAt == null, but by the time the guarded UPDATE
        // ran, another request had already committed the check-in — the
        // UPDATE affects 0 rows, and the service re-reads to report the
        // winner's timestamp rather than claiming false success.
        CheckInService service = service();
        UUID bookingId = UUID.randomUUID();
        Booking booking = booking(bookingId, EVENT_ID, "confirmed", null);
        OffsetDateTime winnerCheckedInAt = OffsetDateTime.now();
        Booking afterRace = booking(bookingId, EVENT_ID, "confirmed", winnerCheckedInAt);

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking), Optional.of(afterRace));
        when(bookingRepository.checkIn(eq(bookingId), eq(EVENT_ID), any(), eq(ORGANIZER_ID))).thenReturn(0);
        stubEmptyAttendeeLookups();

        CheckInResponse response = service.checkIn(EVENT_ID, ORGANIZER_ID, bookingId, null);

        assertThat(response.success()).isFalse();
        assertThat(response.status()).isEqualTo("already_checked_in");
        assertThat(response.checkedInAt()).isEqualTo(winnerCheckedInAt);
    }

    @Test
    void checkIn_attendeeInfoIncludesTicketTypeCounts_andFallsBackToGuestForMissingProfile() {
        CheckInService service = service();
        UUID bookingId = UUID.randomUUID();
        Booking booking = booking(bookingId, EVENT_ID, "confirmed", null);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(bookingRepository.checkIn(eq(bookingId), eq(EVENT_ID), any(), eq(ORGANIZER_ID))).thenReturn(1);
        when(profileRepository.findById(anyString())).thenReturn(Optional.empty());

        UUID ticketTypeId = UUID.randomUUID();
        BookingItem item = newInstance(BookingItem.class);
        set(item, "id", UUID.randomUUID());
        set(item, "bookingId", bookingId);
        set(item, "ticketTypeId", ticketTypeId);
        set(item, "quantity", 2);
        when(bookingItemRepository.findByBookingId(bookingId)).thenReturn(List.of(item));

        TicketType tt = newInstance(TicketType.class);
        set(tt, "id", ticketTypeId);
        set(tt, "name", "VIP");
        when(ticketTypeRepository.findAllById(List.of(ticketTypeId))).thenReturn(List.of(tt));

        CheckInResponse response = service.checkIn(EVENT_ID, ORGANIZER_ID, bookingId, null);

        assertThat(response.attendee().name()).isEqualTo("Guest");
        assertThat(response.attendee().email()).isEqualTo("");
        assertThat(response.attendee().ticketTypes()).hasSize(1);
        assertThat(response.attendee().ticketTypes().get(0).name()).isEqualTo("VIP");
        assertThat(response.attendee().ticketTypes().get(0).quantity()).isEqualTo(2);
    }

    @Test
    void checkIn_prefersBookingIdOverReference_whenBothProvided() {
        CheckInService service = service();
        UUID bookingId = UUID.randomUUID();
        Booking booking = booking(bookingId, EVENT_ID, "confirmed", null);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(bookingRepository.checkIn(eq(bookingId), eq(EVENT_ID), any(), eq(ORGANIZER_ID))).thenReturn(1);
        stubEmptyAttendeeLookups();

        service.checkIn(EVENT_ID, ORGANIZER_ID, bookingId, "SOME-OTHER-REF");

        verify(bookingRepository, never()).findByReference(anyString());
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
