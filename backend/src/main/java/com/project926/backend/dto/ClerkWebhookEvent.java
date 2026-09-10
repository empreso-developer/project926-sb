package com.project926.backend.dto;

/**
 * The top-level shape of a Clerk/Svix webhook delivery — mirrors the
 * existing Next.js {@code ClerkUserEvent} type exactly. Parsed ONLY after
 * {@link com.project926.backend.integration.clerk.ClerkWebhookSignatureVerifier}
 * has verified the raw request body — see ClerkWebhookController.
 */
public record ClerkWebhookEvent(String type, ClerkUserData data) {
}
