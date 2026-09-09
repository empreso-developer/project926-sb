package com.project926.backend.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Mirrors the existing EventRow + nested ticket_types(*) shape returned by
 * both app/(project926)/p/page.tsx (listing) and
 * app/(project926)/p/events/[id]/page.tsx (detail) — both use the
 * identical query shape (.select('*, ticket_types(*)')), so one DTO serves
 * both endpoints, same as the existing TS type
 * (EventRow & { ticket_types: TicketType[] }).
 *
 * No organizer field: neither existing query joins profiles, so none is
 * added here.
 */
public record EventDto(
        UUID id,
        String organizerId,
        String title,
        String description,
        LocalDate eventDate,
        LocalTime eventTime,
        String venue,
        String city,
        String bannerUrl,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<TicketTypeDto> ticketTypes) {
}
