package com.project926.backend.exception;

/**
 * Thrown when a role-update target profile id has no matching row. The
 * existing updateProfileRoleAction's {@code .update().eq('id', profileId)}
 * silently "succeeds" against a nonexistent id (Supabase updates 0 rows
 * without erroring); this is a deliberate hardening to a clean 404 instead
 * of a silent no-op, per the migration brief's explicit error-behavior
 * requirements. Mapped to HTTP 404 by GlobalExceptionHandler.
 */
public class ProfileNotFoundException extends RuntimeException {

    public ProfileNotFoundException(String profileId) {
        super("Profile not found: " + profileId);
    }
}
