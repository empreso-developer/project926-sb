package com.project926.backend.service;

import com.project926.backend.dto.ProfileResponse;
import com.project926.backend.exception.ForbiddenException;
import com.project926.backend.repository.ProfileRepository;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * Mirrors app/(project926)/p/api/profile/route.ts exactly:
 * read-only lookup by the authenticated Clerk user id, defaulting to
 * "customer" when no profile row exists yet — no write, no sync-from-Clerk
 * side effect (that self-healing behavior belongs to
 * lib/auth/server.ts#getProfile / getRoleForUser, which back different,
 * page-rendering code paths not being migrated in Phase B).
 */
@Service
public class ProfileService {

    private final ProfileRepository profileRepository;

    public ProfileService(ProfileRepository profileRepository) {
        this.profileRepository = profileRepository;
    }

    /**
     * @param clerkUserId the authenticated caller's Clerk id (the JWT
     *                    {@code sub} claim) — always a String, never UUID.
     */
    public ProfileResponse getRoleForAuthenticatedUser(String clerkUserId) {
        String role = profileRepository.findById(clerkUserId)
                .map(profile -> profile.getRole())
                .orElse("customer");
        return new ProfileResponse(role);
    }

    /**
     * Phase C's translation of middleware.ts's isOrganizerRoute gate
     * (`resolvedRole === 'organizer' || resolvedRole === 'admin'`, applied
     * to the whole /p/dashboard/organizer(.*) subtree) into a
     * service-level check.
     *
     * This exists because the underlying Next.js server actions
     * (createEventAction, updateEventAction, etc. in lib/actions/events.ts)
     * have NO role check of their own — access is enforced entirely by that
     * page-routing middleware, which Spring Boot has no equivalent of.
     * Reproducing the actual end-user-visible behavior (a customer cannot
     * create/manage events) requires this explicit check here; omitting it
     * would be a real behavior change (any authenticated customer could
     * call the API directly), not a faithful migration.
     */
    public void requireOrganizerOrAdminRole(String clerkUserId) {
        String role = getRoleForAuthenticatedUser(clerkUserId).role();
        if (!Set.of("organizer", "admin").contains(role)) {
            throw new ForbiddenException("Requires organizer or admin role");
        }
    }

    /**
     * Mirrors {@code profile?.role === 'admin'} in
     * lib/auth/server.ts#requireEventOrganizer exactly — used only by that
     * function's Spring equivalent (EventService.requireEventOrganizerOrAdmin),
     * distinct from requireOrganizerOrAdminRole's organizer-OR-admin check.
     */
    public boolean isAdmin(String clerkUserId) {
        return "admin".equals(getRoleForAuthenticatedUser(clerkUserId).role());
    }
}
