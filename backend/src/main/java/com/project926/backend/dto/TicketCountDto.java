package com.project926.backend.dto;

/** Mirrors the {name, quantity} shape used both by the attendees page's per-row ticket list and check-in's attendee.ticketTypes. */
public record TicketCountDto(String name, int quantity) {
}
