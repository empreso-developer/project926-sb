package com.project926.backend.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Exists only to prove, end to end, that a real Clerk-issued JWT is accepted
 * and its subject is surfaced as a plain String (Clerk user ids, e.g.
 * "user_3GjubwvONQwJQcpQRdNtbEUEbP5", are never valid UUIDs — see
 * Profile#id). No business logic belongs here; later phases add real
 * protected endpoints per resource.
 */
@RestController
public class ProtectedTestController {

    @GetMapping("/api/v1/test/protected")
    public Map<String, Object> whoAmI(@AuthenticationPrincipal Jwt jwt) {
        Map<String, Object> body = new LinkedHashMap<>();
        String clerkUserId = jwt.getSubject();
        body.put("clerkUserId", clerkUserId);
        body.put("clerkUserIdType", clerkUserId == null ? "null" : clerkUserId.getClass().getSimpleName());
        body.put("message", "Authenticated via Clerk JWT");
        return body;
    }
}
