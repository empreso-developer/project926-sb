package com.project926.backend.dto;

/**
 * Mirrors the existing upload-banner route's exact response shape:
 * {@code {"url": "..."}} — see BannerUploadService.
 */
public record BannerUploadResponse(String url) {
}
