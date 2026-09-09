package com.project926.backend.service;

import com.project926.backend.dto.CustomerBookingDto;
import com.project926.backend.entity.Booking;
import com.project926.backend.entity.BookingItem;
import com.project926.backend.entity.Event;
import com.project926.backend.entity.Payment;
import com.project926.backend.repository.BookingItemRepository;
import com.project926.backend.repository.BookingRepository;
import com.project926.backend.repository.EventRepository;
import com.project926.backend.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Mocked-repository coverage of CustomerBookingService's mapping logic —
 * mirrors app/(project926)/p/dashboard/customer/page.tsx's own-bookings
 * query. The real query/ordering/aggregation behavior against actual rows
 * is proven separately by CustomerBookingsIT against the dev DB.
 */
@ExtendWith(MockitoExtension.class)
class CustomerBookingServiceTest {

    @Mock private BookingRepository bookingRepository;
    @Mock private EventRepository eventRepository;
    @Mock private BookingItemRepository bookingItemRepository;
    @Mock private PaymentRepository paymentRepository;

    private CustomerBookingService service() {
        return new CustomerBookingService(bookingRepository, eventRepository, bookingItemRepository, paymentRepository);
    }

    private static final String CUSTOMER_ID = "user_customer000000000000";

    private Booking booking(UUID id, UUID eventId, String status, OffsetDateTime createdAt) {
        Booking b = newInstance(Booking.class);
        set(b, "id", id);
        set(b, "reference", "BK-" + id.toString().substring(0, 8));
        set(b, "customerId", CUSTOMER_ID);
        set(b, "eventId", eventId);
        set(b, "status", status);
        set(b, "totalAmount", new BigDecimal("500.00"));
        set(b, "qrCode", status.equals("confirmed") ? "data:image/png;base64,xxx" : null);
        set(b, "checkedInAt", null);
        set(b, "createdAt", createdAt);
        return b;
    }

    private Event event(UUID id, String title) {
        Event e = newInstance(Event.class);
        set(e, "id", id);
        set(e, "title", title);
        set(e, "venue", "Venue");
        set(e, "city", "City");
        return e;
    }

    private BookingItem item(UUID bookingId, UUID ticketTypeId, int quantity) {
        BookingItem i = newInstance(BookingItem.class);
        set(i, "id", UUID.randomUUID());
        set(i, "bookingId", bookingId);
        set(i, "ticketTypeId", ticketTypeId);
        set(i, "quantity", quantity);
        return i;
    }

    private Payment payment(UUID bookingId, String razorpayPaymentId, OffsetDateTime createdAt) {
        Payment p = newInstance(Payment.class);
        set(p, "id", UUID.randomUUID());
        set(p, "bookingId", bookingId);
        set(p, "razorpayPaymentId", razorpayPaymentId);
        set(p, "createdAt", createdAt);
        return p;
    }

    @Test
    void listOwnBookings_customerWithZeroBookings_returnsEmptyList_neverQueriesEventsOrItems() {
        when(bookingRepository.findByCustomerIdOrderByCreatedAtDesc(CUSTOMER_ID)).thenReturn(List.of());

        List<CustomerBookingDto> result = service().listOwnBookings(CUSTOMER_ID);

        assertThat(result).isEmpty();
        verify(eventRepository, never()).findAllById(org.mockito.ArgumentMatchers.any());
        verify(bookingItemRepository, never()).findByBookingIdIn(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void listOwnBookings_preservesRepositoryOrdering() {
        UUID bookingId1 = UUID.randomUUID();
        UUID bookingId2 = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        // Repository already returns created_at DESC — the service must
        // not re-sort or otherwise reorder what it's given.
        Booking newer = booking(bookingId1, eventId, "confirmed", OffsetDateTime.now());
        Booking older = booking(bookingId2, eventId, "confirmed", OffsetDateTime.now().minusDays(1));
        when(bookingRepository.findByCustomerIdOrderByCreatedAtDesc(CUSTOMER_ID)).thenReturn(List.of(newer, older));
        when(eventRepository.findAllById(List.of(eventId))).thenReturn(List.of(event(eventId, "Concert")));
        when(bookingItemRepository.findByBookingIdIn(List.of(bookingId1, bookingId2))).thenReturn(List.of());
        when(paymentRepository.findByBookingIdIn(List.of(bookingId1, bookingId2))).thenReturn(List.of());

        List<CustomerBookingDto> result = service().listOwnBookings(CUSTOMER_ID);

        assertThat(result).extracting(CustomerBookingDto::id).containsExactly(bookingId1, bookingId2);
    }

    @Test
    void listOwnBookings_includesAllStatuses_noFilteringInService() {
        // Mirrors the original query's absence of any .eq('status', ...)
        // clause — pending/confirmed/cancelled all come back; the page
        // itself splits them into stats, not this service.
        UUID pendingId = UUID.randomUUID();
        UUID confirmedId = UUID.randomUUID();
        UUID cancelledId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        when(bookingRepository.findByCustomerIdOrderByCreatedAtDesc(CUSTOMER_ID)).thenReturn(List.of(
            booking(pendingId, eventId, "pending", OffsetDateTime.now()),
            booking(confirmedId, eventId, "confirmed", OffsetDateTime.now()),
            booking(cancelledId, eventId, "cancelled", OffsetDateTime.now())));
        when(eventRepository.findAllById(List.of(eventId))).thenReturn(List.of(event(eventId, "Concert")));
        when(bookingItemRepository.findByBookingIdIn(any3(pendingId, confirmedId, cancelledId))).thenReturn(List.of());
        when(paymentRepository.findByBookingIdIn(any3(pendingId, confirmedId, cancelledId))).thenReturn(List.of());

        List<CustomerBookingDto> result = service().listOwnBookings(CUSTOMER_ID);

        assertThat(result).extracting(CustomerBookingDto::status)
            .containsExactlyInAnyOrder("pending", "confirmed", "cancelled");
    }

    @Test
    void listOwnBookings_summedQuantityAcrossMultipleTicketTypes() {
        UUID bookingId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID ticketTypeA = UUID.randomUUID();
        UUID ticketTypeB = UUID.randomUUID();
        when(bookingRepository.findByCustomerIdOrderByCreatedAtDesc(CUSTOMER_ID))
            .thenReturn(List.of(booking(bookingId, eventId, "confirmed", OffsetDateTime.now())));
        when(eventRepository.findAllById(List.of(eventId))).thenReturn(List.of(event(eventId, "Concert")));
        when(bookingItemRepository.findByBookingIdIn(List.of(bookingId)))
            .thenReturn(List.of(item(bookingId, ticketTypeA, 2), item(bookingId, ticketTypeB, 3)));
        when(paymentRepository.findByBookingIdIn(List.of(bookingId))).thenReturn(List.of());

        List<CustomerBookingDto> result = service().listOwnBookings(CUSTOMER_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).ticketQuantity()).isEqualTo(5);
    }

    @Test
    void listOwnBookings_zeroBookingItems_ticketQuantityIsZero() {
        UUID bookingId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        when(bookingRepository.findByCustomerIdOrderByCreatedAtDesc(CUSTOMER_ID))
            .thenReturn(List.of(booking(bookingId, eventId, "pending", OffsetDateTime.now())));
        when(eventRepository.findAllById(List.of(eventId))).thenReturn(List.of(event(eventId, "Concert")));
        when(bookingItemRepository.findByBookingIdIn(List.of(bookingId))).thenReturn(List.of());
        when(paymentRepository.findByBookingIdIn(List.of(bookingId))).thenReturn(List.of());

        List<CustomerBookingDto> result = service().listOwnBookings(CUSTOMER_ID);

        assertThat(result.get(0).ticketQuantity()).isZero();
    }

    @Test
    void listOwnBookings_noPaymentRowExists_paymentIsNull_notAnObjectWithNullField() {
        // Mirrors the original's {payment && (...)} guard: no payment row
        // at all (Razorpay order-creation failure — see
        // PaymentService.createOrder) means the whole payment line is
        // omitted, not shown with a placeholder.
        UUID bookingId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        when(bookingRepository.findByCustomerIdOrderByCreatedAtDesc(CUSTOMER_ID))
            .thenReturn(List.of(booking(bookingId, eventId, "pending", OffsetDateTime.now())));
        when(eventRepository.findAllById(List.of(eventId))).thenReturn(List.of(event(eventId, "Concert")));
        when(bookingItemRepository.findByBookingIdIn(List.of(bookingId))).thenReturn(List.of());
        when(paymentRepository.findByBookingIdIn(List.of(bookingId))).thenReturn(List.of());

        List<CustomerBookingDto> result = service().listOwnBookings(CUSTOMER_ID);

        assertThat(result.get(0).payment()).isNull();
    }

    @Test
    void listOwnBookings_paymentRowExistsButNotYetVerified_paymentPresentWithNullRazorpayId() {
        // Mirrors payment.razorpay_payment_id?.slice(0,16) ?? '—': the
        // payment row exists (created at order-creation time) but
        // razorpay_payment_id is still null pre-verification — the line
        // IS shown (rendering '—'), unlike the "no row at all" case above.
        UUID bookingId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        when(bookingRepository.findByCustomerIdOrderByCreatedAtDesc(CUSTOMER_ID))
            .thenReturn(List.of(booking(bookingId, eventId, "pending", OffsetDateTime.now())));
        when(eventRepository.findAllById(List.of(eventId))).thenReturn(List.of(event(eventId, "Concert")));
        when(bookingItemRepository.findByBookingIdIn(List.of(bookingId))).thenReturn(List.of());
        when(paymentRepository.findByBookingIdIn(List.of(bookingId)))
            .thenReturn(List.of(payment(bookingId, null, OffsetDateTime.now())));

        List<CustomerBookingDto> result = service().listOwnBookings(CUSTOMER_ID);

        assertThat(result.get(0).payment()).isNotNull();
        assertThat(result.get(0).payment().razorpayPaymentId()).isNull();
    }

    @Test
    void listOwnBookings_verifiedPayment_razorpayIdPresent() {
        UUID bookingId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        when(bookingRepository.findByCustomerIdOrderByCreatedAtDesc(CUSTOMER_ID))
            .thenReturn(List.of(booking(bookingId, eventId, "confirmed", OffsetDateTime.now())));
        when(eventRepository.findAllById(List.of(eventId))).thenReturn(List.of(event(eventId, "Concert")));
        when(bookingItemRepository.findByBookingIdIn(List.of(bookingId))).thenReturn(List.of());
        when(paymentRepository.findByBookingIdIn(List.of(bookingId)))
            .thenReturn(List.of(payment(bookingId, "pay_abc123xyz", OffsetDateTime.now())));

        List<CustomerBookingDto> result = service().listOwnBookings(CUSTOMER_ID);

        assertThat(result.get(0).payment().razorpayPaymentId()).isEqualTo("pay_abc123xyz");
    }

    @Test
    void listOwnBookings_missingEventRow_eventIsNull_doesNotThrow() {
        // Defensive parity with the original's nullable nested `event` —
        // should never happen in practice (events cascade-delete their
        // bookings), but the mapping must not NPE if it somehow did.
        UUID bookingId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        when(bookingRepository.findByCustomerIdOrderByCreatedAtDesc(CUSTOMER_ID))
            .thenReturn(List.of(booking(bookingId, eventId, "confirmed", OffsetDateTime.now())));
        when(eventRepository.findAllById(List.of(eventId))).thenReturn(List.of());
        when(bookingItemRepository.findByBookingIdIn(List.of(bookingId))).thenReturn(List.of());
        when(paymentRepository.findByBookingIdIn(List.of(bookingId))).thenReturn(List.of());

        List<CustomerBookingDto> result = service().listOwnBookings(CUSTOMER_ID);

        assertThat(result.get(0).event()).isNull();
    }

    private static List<UUID> any3(UUID a, UUID b, UUID c) {
        return org.mockito.ArgumentMatchers.argThat(list -> list != null && list.size() == 3
            && list.contains(a) && list.contains(b) && list.contains(c));
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
