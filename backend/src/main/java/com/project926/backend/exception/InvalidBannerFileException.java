package com.project926.backend.exception;

/**
 * Thrown for a missing/oversized/wrong-type banner upload — mirrors the
 * existing upload-banner route's three 400 outcomes exactly ("No file
 * provided", "Unsupported file type", "File too large (max 5MB)"). Mapped
 * to HTTP 400 by GlobalExceptionHandler WITH this exact message text
 * preserved (same pattern as BookingValidationException) — these messages
 * describe a client-fixable input problem, not internal infrastructure
 * detail, so unlike BannerStorageException's generic 500 there is no
 * reason to hide them.
 */
public class InvalidBannerFileException extends RuntimeException {

    public InvalidBannerFileException(String message) {
        super(message);
    }
}
