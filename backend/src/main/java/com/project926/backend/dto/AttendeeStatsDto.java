package com.project926.backend.dto;

/**
 * Mirrors the attendees page's stat cards exactly: totalSold/checkedInQty
 * are summed from confirmed booking_items.quantity (not booking counts —
 * a booking with quantity > 1 is weighted correctly), notCheckedInQty =
 * totalSold - checkedInQty, checkInRate = checkedInQty/totalSold*100 (0
 * when totalSold is 0).
 */
public record AttendeeStatsDto(
    long totalSold,
    long checkedInQty,
    long notCheckedInQty,
    double checkInRate
) {
}
