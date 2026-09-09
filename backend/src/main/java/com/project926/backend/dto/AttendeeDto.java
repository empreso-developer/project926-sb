package com.project926.backend.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Mirrors the fields the existing AttendeesPage's AttendeeRow actually
 * uses (app/(project926)/project926/dashboard/organizer/events/[id]/attendees/page.tsx) —
 * this endpoint has no prior JSON contract (the original is a
 * server-rendered page), so field naming follows this project's
 * established snake_case-in-JSON convention (Phase B/C's EventDto etc.),
 * not a reproduction of any existing wire format.
 */
public record AttendeeDto(
    UUID id,
    String reference,
    BigDecimal totalAmount,
    OffsetDateTime createdAt,
    OffsetDateTime checkedInAt,
    String customerName,
    String customerEmail,
    List<TicketCountDto> tickets
) {
}
