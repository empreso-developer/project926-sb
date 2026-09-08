package com.project926.backend.controller;

import com.project926.backend.dto.ProfileResponse;
import com.project926.backend.service.ProfileService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Mirrors GET /project926/api/profile. Requires authentication (enforced by
 * SecurityConfig's default anyRequest().authenticated() — no request
 * mapping here permits unauthenticated access). The Clerk user id comes
 * only from the validated JWT's `sub` claim, never from any client-supplied
 * parameter, so there is no way to request another user's profile through
 * this endpoint.
 */
@RestController
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping("/api/v1/profile")
    public ProfileResponse getProfile(@AuthenticationPrincipal Jwt jwt) {
        String clerkUserId = jwt.getSubject();
        return profileService.getRoleForAuthenticatedUser(clerkUserId);
    }
}
