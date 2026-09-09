package com.project926.backend.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Mirrors the subset of {@code EventRow} the customer dashboard actually
 * renders (title, date, time, venue, city, banner) — see
 * app/(project926)/p/dashboard/customer/page.tsx#BookingRow. Nullable on
 * the owning {@link CustomerBookingDto} for the same defensive reason the
 * original Supabase nested select allowed a null {@code event}, even
 * though {@code bookings.event_id} cascades from {@code events} in
 * practice (see CustomerBookingService's Javadoc).
 */
public record CustomerBookingEventDto(
    UUID id,
    String title,
    LocalDate eventDate,
    LocalTime eventTime,
    String venue,
    String city,
    String bannerUrl
) {
}
