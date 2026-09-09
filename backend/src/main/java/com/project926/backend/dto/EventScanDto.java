package com.project926.backend.dto;

import java.util.UUID;

/**
 * Minimal event info the organizer scan page needs (see
 * app/(project926)/p/dashboard/organizer/events/[id]/scan/page.tsx) — just
 * enough for the page header. Authorization (owner-or-admin) is enforced
 * by EventService.requireEventOrganizerOrAdmin before this is built, the
 * same shared check used by the check-in and attendees endpoints — not
 * duplicated here.
 */
public record EventScanDto(UUID id, String title) {
}
