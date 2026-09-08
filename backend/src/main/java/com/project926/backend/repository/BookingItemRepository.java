package com.project926.backend.repository;

import com.project926.backend.entity.BookingItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BookingItemRepository extends JpaRepository<BookingItem, UUID> {

    List<BookingItem> findByBookingId(UUID bookingId);
}
