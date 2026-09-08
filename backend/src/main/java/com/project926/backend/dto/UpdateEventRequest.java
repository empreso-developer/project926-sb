package com.project926.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Mirrors lib/validations/event.ts EventSchema — the existing
 * updateEventAction validates with the exact same schema as create, and
 * updates the exact same field set (never touches organizer_id or status).
 * Identical shape to CreateEventRequest by design, kept as a separate type
 * per endpoint for clarity.
 */
public record UpdateEventRequest(
    @NotBlank @Size(min = 3, max = 120) String title,
    @Size(max = 5000) String description,
    @NotNull LocalDate eventDate,
    @NotNull LocalTime eventTime,
    @NotBlank @Size(min = 2, max = 200) String venue,
    @NotBlank @Size(min = 2, max = 100) String city,
    @URL String bannerUrl
) {
}
