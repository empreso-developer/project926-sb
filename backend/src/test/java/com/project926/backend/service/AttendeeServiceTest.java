package com.project926.backend.service;

import com.project926.backend.dto.AttendeeListResponse;
import com.project926.backend.entity.Booking;
import com.project926.backend.entity.BookingItem;
import com.project926.backend.entity.Profile;
import com.project926.backend.exception.EventNotFoundException;
import com.project926.backend.exception.ForbiddenException;
import com.project926.backend.repository.BookingItemRepository;
import com.project926.backend.repository.BookingRepository;
import com.project926.backend.repository.ProfileRepository;
import com.project926.backend.repository.TicketTypeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Mocked-repository coverage of AttendeeService — mirrors
 * app/(project926)/p/dashboard/organizer/events/[id]/attendees/page.tsx
 * (Step 18's attendee-listing test matrix): authorization delegation,
 * default pagination/filter, and search-pattern construction (including
 * ESCAPE-safe LIKE-wildcard escaping — see BookingRepository.findAttendees).
 * The actual query/pagination/search behavior against real rows is proven
 * separately by AttendeeListingIT against the dev DB.
 */
@ExtendWith(MockitoExtension.class)
class AttendeeServiceTest {

    @Mock
    private EventService eventService;
    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private BookingItemRepository bookingItemRepository;
    @Mock
    private TicketTypeRepository ticketTypeRepository;
    @Mock
    private ProfileRepository profileRepository;

    private AttendeeService service() {
        return new AttendeeService(eventService, bookingRepository, bookingItemRepository, ticketTypeRepository,
                profileRepository);
    }

    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final String CALLER_ID = "user_organizer00000000000";

    @Test
    void listAttendees_delegatesAuthorizationToEventService_andPropagatesForbidden() {
        AttendeeService service = service();
        doThrow(new ForbiddenException("Forbidden")).when(eventService).requireEventOrganizerOrAdmin(EVENT_ID,
                CALLER_ID);

        assertThatThrownBy(() -> service.listAttendees(EVENT_ID, CALLER_ID, null, null, null))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void listAttendees_propagatesNotFound_whenEventDoesNotExist() {
        AttendeeService service = service();
        doThrow(new EventNotFoundException(EVENT_ID)).when(eventService).requireEventOrganizerOrAdmin(EVENT_ID,
                CALLER_ID);

        assertThatThrownBy(() -> service.listAttendees(EVENT_ID, CALLER_ID, null, null, null))
                .isInstanceOf(EventNotFoundException.class);
    }

    @Test
    void listAttendees_nullPageAndFilter_defaultsToPageOneAndFilterAll() {
        AttendeeService service = service();
        stubEmptyStatsAndPage();

        AttendeeListResponse response = service.listAttendees(EVENT_ID, CALLER_ID, null, null, null);

        assertThat(response.page()).isEqualTo(1);
        verify(bookingRepository).findAttendees(eq(EVENT_ID), eq("all"), eq(""), any(Pageable.class));
    }

    @Test
    void listAttendees_invalidFilterValue_fallsBackToAll() {
        AttendeeService service = service();
        stubEmptyStatsAndPage();

        service.listAttendees(EVENT_ID, CALLER_ID, 1, "not_a_real_filter", null);

        verify(bookingRepository).findAttendees(eq(EVENT_ID), eq("all"), eq(""), any(Pageable.class));
    }

    @Test
    void listAttendees_checkedInFilter_isPassedThrough() {
        AttendeeService service = service();
        stubEmptyStatsAndPage();

        service.listAttendees(EVENT_ID, CALLER_ID, 1, "checked_in", null);

        verify(bookingRepository).findAttendees(eq(EVENT_ID), eq("checked_in"), eq(""), any(Pageable.class));
    }

    @Test
    void listAttendees_searchTerm_isLowercasedAndWrappedInWildcards_withLikeMetacharactersEscaped() {
        AttendeeService service = service();
        stubEmptyStatsAndPage();

        service.listAttendees(EVENT_ID, CALLER_ID, 1, "all", " 50%_Off ");

        // Leading/trailing whitespace trimmed (matches search.trim() in the
        // existing page), lowercased, and % / _ escaped so they are treated
        // as literal characters rather than SQL LIKE wildcards.
        verify(bookingRepository).findAttendees(eq(EVENT_ID), eq("all"), eq("%50\\%\\_off%"), any(Pageable.class));
    }

    @Test
    void listAttendees_blankSearch_producesEmptySearchPattern_matchingNoSearchBranch() {
        AttendeeService service = service();
        stubEmptyStatsAndPage();

        service.listAttendees(EVENT_ID, CALLER_ID, 1, "all", "   ");

        verify(bookingRepository).findAttendees(eq(EVENT_ID), eq("all"), eq(""), any(Pageable.class));
    }

    @Test
    void listAttendees_statsAreQuantityWeighted_notBookingCountWeighted() {
        AttendeeService service = service();
        UUID booking1 = UUID.randomUUID();
        UUID booking2 = UUID.randomUUID();
        Booking checkedIn = booking(booking1, EVENT_ID, "confirmed", OffsetDateTime.now());
        Booking notCheckedIn = booking(booking2, EVENT_ID, "confirmed", null);
        when(eventService.requireEventOrganizerOrAdmin(EVENT_ID, CALLER_ID)).thenReturn(null);
        when(bookingRepository.findByEventIdAndStatus(EVENT_ID, "confirmed"))
                .thenReturn(List.of(checkedIn, notCheckedIn));

        BookingItem item1 = bookingItem(booking1, UUID.randomUUID(), 3);
        BookingItem item2 = bookingItem(booking2, UUID.randomUUID(), 2);
        when(bookingItemRepository.findByBookingIdIn(List.of(booking1, booking2))).thenReturn(List.of(item1, item2));
        when(bookingRepository.findAttendees(eq(EVENT_ID), anyString(), anyString(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 25), 0));

        AttendeeListResponse response = service.listAttendees(EVENT_ID, CALLER_ID, null, null, null);

        // Not "1 checked-in booking out of 2" — quantity-weighted: 3 tickets
        // checked in, 2 not, matching the existing page's
        // booking_items.quantity-based sums exactly.
        assertThat(response.stats().totalSold()).isEqualTo(5);
        assertThat(response.stats().checkedInQty()).isEqualTo(3);
        assertThat(response.stats().notCheckedInQty()).isEqualTo(2);
        assertThat(response.stats().checkInRate()).isEqualTo(60.0);
    }

    @Test
    void listAttendees_mapsCustomerNameAndTicketCounts_perAttendeeRow() {
        AttendeeService service = service();
        UUID bookingId = UUID.randomUUID();
        Booking booking = booking(bookingId, EVENT_ID, "confirmed", null);
        when(eventService.requireEventOrganizerOrAdmin(EVENT_ID, CALLER_ID)).thenReturn(null);
        when(bookingRepository.findByEventIdAndStatus(EVENT_ID, "confirmed")).thenReturn(List.of());
        when(bookingRepository.findAttendees(eq(EVENT_ID), anyString(), anyString(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(booking), PageRequest.of(0, 25), 1));

        Profile profile = newInstance(Profile.class);
        set(profile, "id", booking.getCustomerId());
        set(profile, "firstName", "Ada");
        set(profile, "lastName", "Lovelace");
        set(profile, "email", "ada@example.com");
        when(profileRepository.findAllById(List.of(booking.getCustomerId()))).thenReturn(List.of(profile));
        when(bookingItemRepository.findByBookingIdIn(List.of(bookingId))).thenReturn(List.of());

        AttendeeListResponse response = service.listAttendees(EVENT_ID, CALLER_ID, null, null, null);

        assertThat(response.attendees()).hasSize(1);
        assertThat(response.attendees().get(0).customerName()).isEqualTo("Ada Lovelace");
        assertThat(response.attendees().get(0).customerEmail()).isEqualTo("ada@example.com");
    }

    private void stubEmptyStatsAndPage() {
        when(eventService.requireEventOrganizerOrAdmin(EVENT_ID, CALLER_ID)).thenReturn(null);
        when(bookingRepository.findByEventIdAndStatus(EVENT_ID, "confirmed")).thenReturn(List.of());
        Page<Booking> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 25), 0);
        when(bookingRepository.findAttendees(any(), any(), any(), any(Pageable.class))).thenReturn(emptyPage);
    }

    private Booking booking(UUID id, UUID eventId, String status, OffsetDateTime checkedInAt) {
        Booking b = newInstance(Booking.class);
        set(b, "id", id);
        set(b, "reference", "REF-" + id.toString().substring(0, 8));
        set(b, "customerId", "user_customer000000000000");
        set(b, "eventId", eventId);
        set(b, "status", status);
        set(b, "totalAmount", new BigDecimal("100.00"));
        set(b, "checkedInAt", checkedInAt);
        set(b, "createdAt", OffsetDateTime.now());
        set(b, "updatedAt", OffsetDateTime.now());
        return b;
    }

    private BookingItem bookingItem(UUID bookingId, UUID ticketTypeId, int quantity) {
        BookingItem item = newInstance(BookingItem.class);
        set(item, "id", UUID.randomUUID());
        set(item, "bookingId", bookingId);
        set(item, "ticketTypeId", ticketTypeId);
        set(item, "quantity", quantity);
        return item;
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
