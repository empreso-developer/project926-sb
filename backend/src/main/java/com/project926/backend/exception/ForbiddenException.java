package com.project926.backend.exception;

/**
 * Authenticated, but not authorized for this specific action — mirrors the
 * existing server actions' `throw new Error('Forbidden')` for ownership
 * failures (see lib/actions/events.ts, lib/actions/admin.ts). Mapped to
 * HTTP 403 by GlobalExceptionHandler.
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
