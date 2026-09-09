package com.project926.backend.service;

import com.project926.backend.dto.CustomerBookingDto;
import com.project926.backend.dto.CustomerBookingEventDto;
import com.project926.backend.dto.CustomerBookingPaymentDto;
import com.project926.backend.entity.Booking;
import com.project926.backend.entity.BookingItem;
import com.project926.backend.entity.Event;
import com.project926.backend.entity.Payment;
import com.project926.backend.repository.BookingItemRepository;
import com.project926.backend.repository.BookingRepository;
import com.project926.backend.repository.EventRepository;
import com.project926.backend.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Mirrors app/(project926)/p/dashboard/customer/page.tsx's own-bookings
 * query exactly:
 * <pre>
 *   supabaseAdmin.from('bookings')
 *     .select('*, event:events(*), booking_items(*, ticket_type:ticket_types(*)), payments(*)')
 *     .eq('customer_id', userId)
 *     .order('created_at', { ascending: false })
 * </pre>
 * Confirmed by direct inspection of the actual page component (not
 * assumed) before implementing:
 * <ul>
 *   <li><b>No status filter</b> — pending/confirmed/cancelled bookings are
 *       all returned; the page itself splits them into "confirmed"/
 *       "pending" counts and a "total spent" sum (confirmed only) purely
 *       client/render-side. This service reproduces the same unfiltered
 *       query; the page keeps doing its own stats split against the
 *       returned list — no behavior moved into Spring that the page
 *       already owned.</li>
 *   <li><b>Ordering</b> — {@code created_at DESC}, exactly.</li>
 *   <li><b>Ticket information</b> — the page only ever renders a single
 *       summed quantity ({@code booking_items.reduce((s,i)=>s+i.quantity,0)}),
 *       never per-ticket-type names — so {@link CustomerBookingDto} carries
 *       just {@code ticketQuantity}, not a full ticket-type breakdown (see
 *       its Javadoc).</li>
 *   <li><b>Payment information</b> — only {@code payments[0].razorpay_payment_id}
 *       is rendered (truncated client-side), gated on whether a payment
 *       row exists at all — see {@link CustomerBookingPaymentDto}'s
 *       Javadoc for why that's a nullable nested object, not a bare
 *       string.</li>
 *   <li><b>QR/ticket info</b> — {@code qr_code} and {@code checked_in_at}
 *       are rendered directly for confirmed bookings with a QR; both
 *       reproduced verbatim.</li>
 *   <li><b>Empty state</b> — the page renders "No bookings yet" when the
 *       list is empty; this service just returns an empty list, same as
 *       the original ({@code bookings ?? []}).</li>
 *   <li><b>Error state</b> — the ORIGINAL code destructures only
 *       {@code { data: bookings }} from the Supabase call and never checks
 *       {@code error} — meaning a query failure there already silently
 *       fell back to an empty list, not a thrown exception. This service
 *       does not swallow errors itself (a genuine Spring-side failure
 *       throws normally, matching every other migrated read endpoint's
 *       convention); the equivalent fail-soft behavior for "backend
 *       unreachable" is preserved on the FRONTEND side instead, in the
 *       Server Component's own try/catch — see the page component,
 *       consistent with every other Phase H-migrated page (homepage,
 *       event detail, etc.), not a new pattern invented for this one.</li>
 *   <li><b>Authorization</b> — the original has no separate authorization
 *       check beyond {@code if (!userId) return null} (middleware already
 *       gates {@code /p/dashboard/customer} for signed-out users). Spring's
 *       equivalent is stronger by construction: {@code customerId} comes
 *       only from the validated Clerk JWT subject
 *       ({@code jwt.getSubject()} in CustomerController), never from any
 *       client-suppliable parameter — there is no code path by which a
 *       caller can request another customer's bookings.</li>
 * </ul>
 */
@Service
public class CustomerBookingService {

    private final BookingRepository bookingRepository;
    private final EventRepository eventRepository;
    private final BookingItemRepository bookingItemRepository;
    private final PaymentRepository paymentRepository;

    public CustomerBookingService(
        BookingRepository bookingRepository,
        EventRepository eventRepository,
        BookingItemRepository bookingItemRepository,
        PaymentRepository paymentRepository
    ) {
        this.bookingRepository = bookingRepository;
        this.eventRepository = eventRepository;
        this.bookingItemRepository = bookingItemRepository;
        this.paymentRepository = paymentRepository;
    }

    @Transactional(readOnly = true)
    public List<CustomerBookingDto> listOwnBookings(String customerId) {
        List<Booking> bookings = bookingRepository.findByCustomerIdOrderByCreatedAtDesc(customerId);
        if (bookings.isEmpty()) {
            return List.of();
        }

        List<UUID> bookingIds = bookings.stream().map(Booking::getId).toList();
        List<UUID> eventIds = bookings.stream().map(Booking::getEventId).distinct().toList();

        Map<UUID, Event> eventsById = eventRepository.findAllById(eventIds).stream()
            .collect(Collectors.toMap(Event::getId, e -> e));

        Map<UUID, Integer> ticketQuantityByBooking = bookingItemRepository.findByBookingIdIn(bookingIds).stream()
            .collect(Collectors.groupingBy(BookingItem::getBookingId, Collectors.summingInt(BookingItem::getQuantity)));

        // At most one payment row per booking in practice, but not
        // guaranteed exactly one — pick the most recently created row per
        // booking id, mirroring findFirstByBookingIdOrderByCreatedAtDesc's
        // semantics (see PaymentRepository#findByBookingIdIn's Javadoc).
        Map<UUID, Payment> paymentByBooking = paymentRepository.findByBookingIdIn(bookingIds).stream()
            .collect(Collectors.toMap(
                Payment::getBookingId,
                p -> p,
                (a, b) -> a.getCreatedAt().isAfter(b.getCreatedAt()) ? a : b));

        return bookings.stream()
            .map(b -> toDto(b, eventsById.get(b.getEventId()),
                ticketQuantityByBooking.getOrDefault(b.getId(), 0),
                paymentByBooking.get(b.getId())))
            .toList();
    }

    private CustomerBookingDto toDto(Booking booking, Event event, int ticketQuantity, Payment payment) {
        CustomerBookingEventDto eventDto = event == null ? null : new CustomerBookingEventDto(
            event.getId(),
            event.getTitle(),
            event.getEventDate(),
            event.getEventTime(),
            event.getVenue(),
            event.getCity(),
            event.getBannerUrl());

        CustomerBookingPaymentDto paymentDto = payment == null ? null
            : new CustomerBookingPaymentDto(payment.getRazorpayPaymentId());

        return new CustomerBookingDto(
            booking.getId(),
            booking.getReference(),
            booking.getStatus(),
            booking.getTotalAmount(),
            booking.getQrCode(),
            booking.getCheckedInAt(),
            booking.getCreatedAt(),
            ticketQuantity,
            eventDto,
            paymentDto);
    }
}
