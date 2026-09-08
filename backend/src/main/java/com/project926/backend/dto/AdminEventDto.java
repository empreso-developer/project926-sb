package com.project926.backend.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Mirrors the admin dashboard's actual query shape exactly:
 * {@code .select('*, organizer:profiles!events_organizer_id_fkey(*)')} —
 * see app/(project926)/project926/dashboard/admin/page.tsx. That query
 * joins the organizer's profile (used there to show the organizer's email)
 * but does NOT select ticket_types, so this DTO has no ticketTypes field,
 * unlike EventDto (Phase B) / the organizer's own detail view.
 */
public record AdminEventDto(
    UUID id,
    String organizerId,
    String organizerEmail,
    String organizerFirstName,
    String organizerLastName,
    String title,
    String description,
    LocalDate eventDate,
    LocalTime eventTime,
    String venue,
    String city,
    String bannerUrl,
    String status,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
}
