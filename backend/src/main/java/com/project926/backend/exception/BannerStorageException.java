package com.project926.backend.exception;

/**
 * Thrown when SupabaseStorageGateway's upload call fails (network error,
 * non-2xx response, or missing configuration). Deliberately unmapped in
 * GlobalExceptionHandler — falls through to its generic Exception handler
 * (HTTP 500, fixed body {"error":"Internal server error"}), a deliberate
 * SECURITY IMPROVEMENT over the existing Next.js route, which returns the
 * raw Supabase Storage error message directly to the browser. The actual
 * cause is still logged server-side in full by SupabaseStorageGateway.
 */
public class BannerStorageException extends RuntimeException {

    public BannerStorageException(String message) {
        super(message);
    }

    public BannerStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
