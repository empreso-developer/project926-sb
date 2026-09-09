package com.project926.backend.dto;

import java.util.List;

/**
 * Mirrors the existing attendees page's data (rows + stats + pagination) —
 * PAGE_SIZE=25, page/filter/q query params, all preserved (see
 * AttendeeService).
 */
public record AttendeeListResponse(
    List<AttendeeDto> attendees,
    AttendeeStatsDto stats,
    int page,
    int pageSize,
    int totalPages,
    long totalCount
) {
}
