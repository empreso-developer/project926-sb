package com.project926.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Mirrors updateProfileRoleAction's role parameter. The existing Server
 * Action only enforces the accepted value set at TypeScript compile time
 * (no runtime check) — an out-of-range value would previously fail at the
 * database's {@code profiles.role} CHECK constraint (see supabase/schema.sql)
 * and surface as an unhandled error. Validating the same three values here
 * and returning a clean 400 is a deliberate, non-behavioral hardening: it
 * accepts exactly what the database already accepted, just with a proper
 * error instead of a constraint-violation crash.
 */
public record UpdateProfileRoleRequest(
    @NotBlank @Pattern(regexp = "customer|organizer|admin") String role) {
}
