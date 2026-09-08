package com.project926.backend.exception;

import java.util.UUID;

/**
 * Mirrors deleteTicketTypeAction's `throw new Error('Ticket type not
 * found')`. Mapped to HTTP 404 by GlobalExceptionHandler.
 */
public class TicketTypeNotFoundException extends RuntimeException {

    public TicketTypeNotFoundException(UUID ticketTypeId) {
        super("Ticket type not found: " + ticketTypeId);
    }
}
