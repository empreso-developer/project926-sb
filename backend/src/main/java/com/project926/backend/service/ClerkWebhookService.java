package com.project926.backend.service;

import com.project926.backend.dto.ClerkEmailAddress;
import com.project926.backend.dto.ClerkUserData;
import com.project926.backend.repository.ProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * Mirrors app/(project926)/p/api/webhooks/clerk/route.ts's actual profile
 * behavior exactly (see the migration audit for the full field-by-field
 * trace this was built from):
 * <ul>
 * <li>{@code user.created}/{@code user.updated} both upsert the same way
 * (the original code handles them identically) — email is the address
 * whose id matches {@code primary_email_address_id}, falling back to the
 * first address, falling back to {@code ""} if there are none.</li>
 * <li>role is {@code unsafe_metadata.role}, else {@code public_metadata.role},
 * else {@code "customer"} — then re-validated against the exact
 * {customer, organizer, admin} set regardless of which branch produced
 * it, defaulting to {@code "customer"} for anything else (mirrors the
 * original's redundant-looking but deliberate double check).</li>
 * <li>{@code user.deleted} deletes the row — this webhook is the only
 * thing in the app that ever deletes a profiles row; the original never
 * questioned whether deleting a Clerk user should delete the profile, it
 * just does it, so this preserves that exactly rather than "fixing" it.</li>
 * <li>any other event type is silently ignored (the original's if/else-if
 * chain has no else branch — it just falls through to a 200 response).</li>
 * </ul>
 *
 * <h2>Idempotency</h2>
 * No new webhook-delivery-id/event-id table — same policy as
 * RazorpayWebhookService (Phase G): the existing {@code profiles} primary
 * key plus atomic upsert/delete queries (ProfileRepository.upsertFromClerk/
 * deleteByIdSafe) are already sufficient. A duplicate user.created is
 * literally the same upsert twice (converges on the same row); a
 * duplicate user.deleted is a delete-by-id that safely affects 0 rows the
 * second time.
 */
@Service
public class ClerkWebhookService {

    private static final Logger log = LoggerFactory.getLogger(ClerkWebhookService.class);
    private static final Set<String> ALLOWED_ROLES = Set.of("customer", "organizer", "admin");

    private final ProfileRepository profileRepository;

    public ClerkWebhookService(ProfileRepository profileRepository) {
        this.profileRepository = profileRepository;
    }

    public void process(String eventType, ClerkUserData data) {
        switch (eventType) {
            case "user.created", "user.updated" -> handleUpsert(data);
            case "user.deleted" -> handleDeleted(data);
            default -> log.info("[clerk-webhook] Ignoring unsupported event type: {}", eventType);
        }
    }

    private void handleUpsert(ClerkUserData data) {
        String email = resolvePrimaryEmail(data);
        String role = resolveRole(data);
        profileRepository.upsertFromClerk(data.id(), email, data.firstName(), data.lastName(), role);
        log.info("[clerk-webhook] Upserted profile {} (role={})", data.id(), role);
    }

    private void handleDeleted(ClerkUserData data) {
        int deleted = profileRepository.deleteByIdSafe(data.id());
        log.info("[clerk-webhook] Delete for profile {}: {} row(s) affected", data.id(), deleted);
    }

    private static String resolvePrimaryEmail(ClerkUserData data) {
        return data.emailAddresses().stream()
                .filter(e -> e.id() != null && e.id().equals(data.primaryEmailAddressId()))
                .findFirst()
                .map(ClerkEmailAddress::emailAddress)
                .orElseGet(() -> data.emailAddresses().isEmpty()
                        ? ""
                        : data.emailAddresses().get(0).emailAddress());
    }

    private static String resolveRole(ClerkUserData data) {
        String candidate = firstNonBlank(
                asNonBlankString(data.unsafeMetadata().get("role")),
                asNonBlankString(data.publicMetadata().get("role")));
        String role = candidate != null ? candidate : "customer";
        return ALLOWED_ROLES.contains(role) ? role : "customer";
    }

    private static String asNonBlankString(Object value) {
        return (value instanceof String s && !s.isBlank()) ? s : null;
    }

    private static String firstNonBlank(String a, String b) {
        return a != null ? a : b;
    }
}
