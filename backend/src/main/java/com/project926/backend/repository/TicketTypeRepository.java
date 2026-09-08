package com.project926.backend.repository;

import com.project926.backend.entity.TicketType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TicketTypeRepository extends JpaRepository<TicketType, UUID> {

    /**
     * No explicit ordering: mirrors the existing nested Supabase select
     * (.select('*, ticket_types(*)')), which does not specify an order for
     * the nested ticket_types either.
     */
    List<TicketType> findByEventId(UUID eventId);
}
