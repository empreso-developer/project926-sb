package com.project926.backend.repository;

import com.project926.backend.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, UUID> {

    /**
     * Mirrors the existing approved-events listing query exactly:
     * {@code .eq('status', 'approved').order('event_date', { ascending: true })}
     * — see app/(project926)/project926/page.tsx#getApprovedEvents.
     */
    List<Event> findByStatusOrderByEventDateAsc(String status);

    /**
     * Mirrors the organizer dashboard's own-events query ordering:
     * {@code .eq('organizer_id', userId).order('created_at', { ascending: false })}
     * — see app/(project926)/project926/dashboard/organizer/page.tsx.
     */
    List<Event> findByOrganizerIdOrderByCreatedAtDesc(String organizerId);

    /**
     * Mirrors the admin dashboard's all-events query ordering:
     * {@code .order('created_at', { ascending: false })}
     * — see app/(project926)/project926/dashboard/admin/page.tsx.
     */
    List<Event> findAllByOrderByCreatedAtDesc();
}
