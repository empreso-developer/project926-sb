package com.project926.backend.dto;

import java.time.OffsetDateTime;

/**
 * Mirrors the admin dashboard's Users table exactly (email, name, role,
 * joined date) — see app/(project926)/p/dashboard/admin/page.tsx. The
 * existing query selects every profile column, but the UI only ever reads
 * these fields, so {@code updatedAt} is deliberately omitted here (avoids
 * over-exposing profile data the admin UI never displays).
 */
public record AdminUserDto(
    String id,
    String email,
    String firstName,
    String lastName,
    String role,
    OffsetDateTime createdAt) {
}
