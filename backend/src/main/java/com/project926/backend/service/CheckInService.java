package com.project926.backend.service;

import com.project926.backend.dto.CheckInResponse;
import com.project926.backend.dto.TicketCountDto;
import com.project926.backend.entity.Booking;
import com.project926.backend.entity.BookingItem;
import com.project926.backend.entity.Profile;
import com.project926.backend.entity.TicketType;
import com.project926.backend.repository.BookingItemRepository;
import com.project926.backend.repository.BookingRepository;
import com.project926.backend.repository.ProfileRepository;
import com.project926.backend.repository.TicketTypeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Mirrors app/(project926)/p/api/organizer/events/[eventId]/check-in/route.ts
 * exactly, INCLUDING its unusual response contract (see CheckInResponse's
 * Javadoc): this method never throws for a business-outcome case — every
 * one of them (booking not found, wrong event, pending, cancelled, already
 * checked in, checked in) is represented as a returned CheckInResponse
 * value, matching the existing route's uniform HTTP 200 across all of
 * these. Organizer/admin authorization (which the existing route handles
 * with its own distinct 401/404/403 status codes) is NOT done here — see
 * CheckInController, which mirrors that specific part of the route's
 * structure instead, since it's genuinely coupled to HTTP status codes
 * this service has no business choosing.
 */
@Service
public class CheckInService {

    private static final Logger log = LoggerFactory.getLogger(CheckInService.class);

    private final BookingRepository bookingRepository;
    private final BookingItemRepository bookingItemRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final ProfileRepository profileRepository;

    public CheckInService(
            BookingRepository bookingRepository,
            BookingItemRepository bookingItemRepository,
            TicketTypeRepository ticketTypeRepository,
            ProfileRepository profileRepository) {
        this.bookingRepository = bookingRepository;
        this.bookingItemRepository = bookingItemRepository;
        this.ticketTypeRepository = ticketTypeRepository;
        this.profileRepository = profileRepository;
    }

    /**
     * @param eventId   the event from the URL path — already verified as
     *                  belonging to this organizer/admin by the caller.
     *                  THIS is what's checked against, never anything the
     *                  QR itself claims (see checkIn(bookingId, eventId, ...)
     *                  in BookingRepository — the event relationship is
     *                  part of the guarded UPDATE's WHERE clause).
     * @param callerId  the authenticated organizer/admin's Clerk id —
     *                  recorded as checked_in_by on success.
     * @param bookingId looked up first if present (mirrors the route's
     *                  {@code booking_id ? ... : ...} branch).
     * @param reference used only if bookingId is null.
     */
    @Transactional
    public CheckInResponse checkIn(UUID eventId, String callerId, UUID bookingId, String reference) {
        Optional<Booking> bookingOpt = bookingId != null
                ? bookingRepository.findById(bookingId)
                : bookingRepository.findByReference(reference);

        if (bookingOpt.isEmpty()) {
            return CheckInResponse.status("invalid");
        }
        Booking booking = bookingOpt.get();

        // The event being scanned for is the URL's eventId (already
        // verified by the caller) — never anything the request/QR claims.
        // This check is defense-in-depth on top of the guarded UPDATE
        // below, which enforces the same relationship atomically.
        if (!booking.getEventId().equals(eventId)) {
            return CheckInResponse.status("wrong_event");
        }

        if (!"confirmed".equals(booking.getStatus())) {
            return CheckInResponse
                    .status("pending".equals(booking.getStatus()) ? "payment_not_confirmed" : "cancelled");
        }

        if (booking.getCheckedInAt() != null) {
            return CheckInResponse.alreadyCheckedIn(
                    loadAttendeeInfo(booking.getCustomerId(), booking.getId()),
                    booking.getReference(),
                    booking.getCheckedInAt());
        }

        OffsetDateTime now = OffsetDateTime.now();
        int rowsUpdated = bookingRepository.checkIn(booking.getId(), eventId, now, callerId);

        if (rowsUpdated == 0) {
            // Lost the race: someone else's request claimed it a moment
            // ago (or, less likely, the booking's event/status changed
            // between the reads above and this UPDATE — either way, the
            // guarded UPDATE's WHERE clause is the actual authority, not
            // the Java-side checks above it).
            Booking current = bookingRepository.findById(booking.getId()).orElse(booking);
            return CheckInResponse.alreadyCheckedIn(
                    loadAttendeeInfo(booking.getCustomerId(), booking.getId()),
                    booking.getReference(),
                    current.getCheckedInAt());
        }

        log.info("[check-in] Booking {} checked in by {} for event {}", booking.getReference(), callerId, eventId);
        return CheckInResponse.checkedIn(
                loadAttendeeInfo(booking.getCustomerId(), booking.getId()),
                booking.getReference(),
                now);
    }

    /** Mirrors loadAttendeeInfo() in the existing route exactly. */
    private CheckInResponse.AttendeeInfo loadAttendeeInfo(String customerId, UUID bookingId) {
        Profile profile = profileRepository.findById(customerId).orElse(null);
        String name = joinNonBlank(
                profile != null ? profile.getFirstName() : null,
                profile != null ? profile.getLastName() : null);
        String email = profile != null && profile.getEmail() != null ? profile.getEmail() : "";

        List<BookingItem> items = bookingItemRepository.findByBookingId(bookingId);
        List<UUID> ticketTypeIds = items.stream().map(BookingItem::getTicketTypeId).distinct().toList();
        var ticketTypesById = ticketTypeRepository.findAllById(ticketTypeIds).stream()
                .collect(java.util.stream.Collectors.toMap(TicketType::getId, t -> t));

        List<TicketCountDto> ticketTypes = items.stream()
                .map(i -> new TicketCountDto(
                        ticketTypesById.containsKey(i.getTicketTypeId())
                                ? ticketTypesById.get(i.getTicketTypeId()).getName()
                                : "Ticket",
                        i.getQuantity()))
                .toList();

        return new CheckInResponse.AttendeeInfo(name, email, ticketTypes);
    }

    private static String joinNonBlank(String first, String last) {
        String joined = java.util.stream.Stream.of(first, last)
                .filter(s -> s != null && !s.isBlank())
                .collect(java.util.stream.Collectors.joining(" "))
                .trim();
        return joined.isEmpty() ? "Guest" : joined;
    }
}
