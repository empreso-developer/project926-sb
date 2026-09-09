package com.project926.backend.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Mirrors app/(project926)/p/dashboard/customer/page.tsx's own-bookings
 * query and its {@code CustomerBooking}/{@code BookingRow} usage exactly —
 * see CustomerBookingService's Javadoc for the full field-by-field audit.
 * No prior JSON contract existed for this (server-rendered page), so this
 * follows the project's established snake_case convention (Phase F's
 * AttendeeDto took the same approach) rather than inventing a new shape.
 *
 * {@code ticketQuantity} replaces the original's client-side
 * {@code booking.booking_items.reduce((s,i)=>s+i.quantity,0)} — computed
 * server-side instead (same resulting number), since the page never
 * renders per-ticket-type names, only the total count.
 */
public record CustomerBookingDto(
    UUID id,
    String reference,
    String status,
    BigDecimal totalAmount,
    String qrCode,
    OffsetDateTime checkedInAt,
    OffsetDateTime createdAt,
    int ticketQuantity,
    CustomerBookingEventDto event,
    CustomerBookingPaymentDto payment
) {
}
