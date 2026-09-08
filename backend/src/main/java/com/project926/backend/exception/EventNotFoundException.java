package com.project926.backend.exception;

import java.util.UUID;

/**
 * Thrown when an event id has no matching row — mirrors the existing
 * Next.js event-detail page calling notFound() when getEvent() returns
 * null. Mapped to HTTP 404 by GlobalExceptionHandler.
 */
public class EventNotFoundException extends RuntimeException {

    public EventNotFoundException(UUID eventId) {
        super("Event not found: " + eventId);
    }
}
