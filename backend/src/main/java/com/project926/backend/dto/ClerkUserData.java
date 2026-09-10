package com.project926.backend.dto;

import java.util.List;
import java.util.Map;

/**
 * Mirrors the existing Next.js webhook's {@code ClerkUserEvent["data"]}
 * shape exactly (see app/(project926)/p/api/webhooks/clerk/route.ts) —
 * only the fields that route actually read. {@code user.deleted} payloads
 * carry a much smaller object (just {@code id}, no email/name/metadata),
 * so every field besides {@code id} is nullable here; the compact
 * constructor normalizes null collections to empty ones so
 * ClerkWebhookService never has to null-check them.
 */
public record ClerkUserData(
    String id,
    List<ClerkEmailAddress> emailAddresses,
    String primaryEmailAddressId,
    String firstName,
    String lastName,
    Map<String, Object> publicMetadata,
    Map<String, Object> unsafeMetadata
) {
    public ClerkUserData {
        if (emailAddresses == null) emailAddresses = List.of();
        if (publicMetadata == null) publicMetadata = Map.of();
        if (unsafeMetadata == null) unsafeMetadata = Map.of();
    }
}
