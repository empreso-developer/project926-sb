package com.project926.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Mirrors lib/validations/event.ts EventSchema exactly (title 3-120,
 * description optional/max 5000, venue 2-200, city 2-100, banner_url
 * optional valid URL). No organizerId or status field — organizerId comes
 * from the authenticated JWT subject, status is always "draft" for a new
 * event, both hardcoded server-side exactly as createEventAction does.
 *
 * event_date/event_time are typed dates rather than the raw strings the
 * existing zod schema accepts — a stricter but not behaviorally different
 * check, since the existing UI always sends well-formed values from a date
 * picker and the database column is a real `date`/`time` type either way.
 */
public record CreateEventRequest(
    @NotBlank @Size(min = 3, max = 120) String title,
    @Size(max = 5000) String description,
    @NotNull LocalDate eventDate,
    @NotNull LocalTime eventTime,
    @NotBlank @Size(min = 2, max = 200) String venue,
    @NotBlank @Size(min = 2, max = 100) String city,
    @URL String bannerUrl
) {
}
