package com.project926.backend.dto;

import java.math.BigDecimal;

/**
 * Mirrors the admin dashboard's Revenue stat exactly:
 * {@code bookings.filter(b => b.status === 'confirmed').reduce((s,b) => s + Number(b.total_amount), 0)}
 * — see app/(project926)/p/dashboard/admin/page.tsx. Computed as a
 * database SUM aggregate (AdminUserService/BookingRepository) rather than
 * loading every booking into Java.
 */
public record AdminRevenueDto(BigDecimal totalRevenue) {
}
