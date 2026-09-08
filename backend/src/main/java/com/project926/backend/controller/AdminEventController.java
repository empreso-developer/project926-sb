package com.project926.backend.controller;

import com.project926.backend.dto.AdminEventDto;
import com.project926.backend.service.EventModerationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Mirrors lib/actions/admin.ts's requireAdmin()-gated event actions
 * (approveEventAction / rejectEventAction / removeEventAction) and the
 * admin dashboard's all-events read. The admin-role check and the
 * (deliberate) absence of any ownership check both live in
 * EventModerationService — any admin may act on any event, matching the
 * existing code exactly.
 */
@RestController
@RequestMapping("/api/v1/admin/events")
public class AdminEventController {

    private final EventModerationService eventModerationService;

    public AdminEventController(EventModerationService eventModerationService) {
        this.eventModerationService = eventModerationService;
    }

    @GetMapping
    public List<AdminEventDto> listAllEvents(@AuthenticationPrincipal Jwt jwt) {
        return eventModerationService.listAllEventsForAdmin(jwt.getSubject());
    }

    @PostMapping("/{eventId}/approve")
    public ResponseEntity<Void> approveEvent(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID eventId) {
        eventModerationService.approveEvent(eventId, jwt.getSubject());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{eventId}/reject")
    public ResponseEntity<Void> rejectEvent(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID eventId) {
        eventModerationService.rejectEvent(eventId, jwt.getSubject());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{eventId}")
    public ResponseEntity<Void> removeEvent(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID eventId) {
        eventModerationService.removeEvent(eventId, jwt.getSubject());
        return ResponseEntity.noContent().build();
    }
}
