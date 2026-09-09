package com.project926.backend.service;

import com.project926.backend.dto.AttendeeDto;
import com.project926.backend.dto.AttendeeListResponse;
import com.project926.backend.dto.AttendeeStatsDto;
import com.project926.backend.dto.TicketCountDto;
import com.project926.backend.entity.Booking;
import com.project926.backend.entity.BookingItem;
import com.project926.backend.entity.Profile;
import com.project926.backend.entity.TicketType;
import com.project926.backend.repository.BookingItemRepository;
import com.project926.backend.repository.BookingRepository;
import com.project926.backend.repository.ProfileRepository;
import com.project926.backend.repository.TicketTypeRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Mirrors app/(project926)/p/dashboard/organizer/events/[id]/attendees/page.tsx
 * exactly: stats computed from confirmed booking_items.quantity (not
 * booking counts), PAGE_SIZE=25, filter (all/checked_in/not_checked_in),
 * search (booking reference OR customer name/email), ordered by
 * created_at descending. No prior JSON contract existed for this
 * (server-rendered page) — see AttendeeDto's Javadoc.
 */
@Service
public class AttendeeService {

    private static final int PAGE_SIZE = 25;
    private static final Set<String> VALID_FILTERS = Set.of("checked_in", "not_checked_in");

    private final EventService eventService;
    private final BookingRepository bookingRepository;
    private final BookingItemRepository bookingItemRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final ProfileRepository profileRepository;

    public AttendeeService(
            EventService eventService,
            BookingRepository bookingRepository,
            BookingItemRepository bookingItemRepository,
            TicketTypeRepository ticketTypeRepository,
            ProfileRepository profileRepository) {
        this.eventService = eventService;
        this.bookingRepository = bookingRepository;
        this.bookingItemRepository = bookingItemRepository;
        this.ticketTypeRepository = ticketTypeRepository;
        this.profileRepository = profileRepository;
    }

    @Transactional(readOnly = true)
    public AttendeeListResponse listAttendees(UUID eventId, String callerId, Integer pageParam, String filterParam,
            String searchParam) {
        // Throws EventNotFoundException (404) / ForbiddenException (403) —
        // handled generically by GlobalExceptionHandler, matching this
        // project's established convention for read endpoints (Phase B/C).
        eventService.requireEventOrganizerOrAdmin(eventId, callerId);

        int page = pageParam == null || pageParam < 1 ? 1 : pageParam;
        String filter = filterParam != null && VALID_FILTERS.contains(filterParam) ? filterParam : "all";
        String search = searchParam == null ? "" : searchParam.trim();
        String searchPattern = search.isEmpty() ? "" : "%" + escapeLike(search.toLowerCase()) + "%";

        AttendeeStatsDto stats = computeStats(eventId);

        Page<Booking> bookingPage = bookingRepository.findAttendees(
                eventId, filter, searchPattern, PageRequest.of(page - 1, PAGE_SIZE));

        List<Booking> bookings = bookingPage.getContent();
        List<UUID> bookingIds = bookings.stream().map(Booking::getId).toList();
        List<String> customerIds = bookings.stream().map(Booking::getCustomerId).distinct().toList();

        Map<String, Profile> profilesById = profileRepository.findAllById(customerIds).stream()
                .collect(Collectors.toMap(Profile::getId, p -> p));
        Map<UUID, List<TicketCountDto>> ticketsByBooking = groupTicketCountsByBooking(bookingIds);

        List<AttendeeDto> attendees = bookings.stream()
                .map(b -> toAttendeeDto(b, profilesById.get(b.getCustomerId()),
                        ticketsByBooking.getOrDefault(b.getId(), List.of())))
                .toList();

        return new AttendeeListResponse(
                attendees,
                stats,
                page,
                PAGE_SIZE,
                Math.max(1, bookingPage.getTotalPages()),
                bookingPage.getTotalElements());
    }

    private AttendeeStatsDto computeStats(UUID eventId) {
        List<Booking> confirmedBookings = bookingRepository.findByEventIdAndStatus(eventId, "confirmed");
        Map<UUID, Boolean> checkedInByBookingId = confirmedBookings.stream()
                .collect(Collectors.toMap(Booking::getId, b -> b.getCheckedInAt() != null));

        List<UUID> confirmedBookingIds = confirmedBookings.stream().map(Booking::getId).toList();
        List<BookingItem> items = confirmedBookingIds.isEmpty() ? List.of()
                : bookingItemRepository.findByBookingIdIn(confirmedBookingIds);

        long totalSold = 0;
        long checkedInQty = 0;
        for (BookingItem item : items) {
            totalSold += item.getQuantity();
            if (Boolean.TRUE.equals(checkedInByBookingId.get(item.getBookingId()))) {
                checkedInQty += item.getQuantity();
            }
        }
        long notCheckedInQty = totalSold - checkedInQty;
        double checkInRate = totalSold > 0 ? (checkedInQty * 100.0) / totalSold : 0.0;

        return new AttendeeStatsDto(totalSold, checkedInQty, notCheckedInQty, checkInRate);
    }

    private Map<UUID, List<TicketCountDto>> groupTicketCountsByBooking(List<UUID> bookingIds) {
        if (bookingIds.isEmpty()) {
            return Map.of();
        }
        List<BookingItem> items = bookingItemRepository.findByBookingIdIn(bookingIds);
        List<UUID> ticketTypeIds = items.stream().map(BookingItem::getTicketTypeId).distinct().toList();
        Map<UUID, TicketType> ticketTypesById = ticketTypeRepository.findAllById(ticketTypeIds).stream()
                .collect(Collectors.toMap(TicketType::getId, t -> t));

        return items.stream().collect(Collectors.groupingBy(
                BookingItem::getBookingId,
                Collectors.mapping(item -> new TicketCountDto(
                        ticketTypesById.containsKey(item.getTicketTypeId())
                                ? ticketTypesById.get(item.getTicketTypeId()).getName()
                                : "Ticket",
                        item.getQuantity()),
                        Collectors.toList())));
    }

    private AttendeeDto toAttendeeDto(Booking booking, Profile profile, List<TicketCountDto> tickets) {
        String customerName = profile == null
                ? "Guest"
                : joinNonBlank(profile.getFirstName(), profile.getLastName(), "Guest");
        String customerEmail = profile != null && profile.getEmail() != null ? profile.getEmail() : "";

        return new AttendeeDto(
                booking.getId(),
                booking.getReference(),
                booking.getTotalAmount(),
                booking.getCreatedAt(),
                booking.getCheckedInAt(),
                customerName,
                customerEmail,
                tickets);
    }

    private static String joinNonBlank(String first, String last, String fallback) {
        String joined = java.util.stream.Stream.of(first, last)
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.joining(" "))
                .trim();
        return joined.isEmpty() ? fallback : joined;
    }

    /**
     * The existing page escapes {@code %_,()} before building a PostgREST
     * {@code .or()} filter string — but {@code ,} and {@code ()} are
     * PostgREST's own filter-string syntax (comma separates OR-conditions,
     * parens group them), not SQL LIKE metacharacters. This service issues
     * a plain JPQL {@code LIKE ... ESCAPE '\'} instead (see
     * BookingRepository#findAttendees), so only the two characters that are
     * actually LIKE wildcards need escaping here; a literal comma or paren
     * in a search term has no special meaning in a Postgres LIKE pattern
     * and needs no escaping in this implementation.
     */
    private static String escapeLike(String value) {
        return value.replace("%", "\\%").replace("_", "\\_");
    }
}
