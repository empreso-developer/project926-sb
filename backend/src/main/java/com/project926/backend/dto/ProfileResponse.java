package com.project926.backend.dto;

/**
 * Matches the existing {@code GET /project926/api/profile} response shape
 * exactly: {@code {"role": "customer" | "organizer" | "admin"}}. The
 * existing frontend (components/site-header.tsx) only ever reads
 * {@code data.role} from this endpoint, so no other profile field is
 * exposed here — adding more would go beyond reproducing existing
 * behavior.
 */
public record ProfileResponse(String role) {
}
