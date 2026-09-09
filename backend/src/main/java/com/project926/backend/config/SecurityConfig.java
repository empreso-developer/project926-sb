package com.project926.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Authentication model for the whole backend: Clerk issues the JWT, Spring
 * Security validates it as an OAuth2 resource server (signature via Clerk's
 * JWKS endpoint, issuer via {@code jwt.issuer-uri}) — see application-dev.yml
 * for the actual endpoints. There is no session, no cookie-based auth, and
 * no second authentication system; Clerk stays the sole identity provider,
 * per the migration brief.
 *
 * Public without a token: health/actuator, and GET /api/v1/events(/**) —
 * mirroring the existing Next.js event listing/detail pages, which are
 * public today (see app/(project926)/p/page.tsx and
 * .../events/[id]/page.tsx). Everything else, including
 * GET /api/v1/profile, requires a valid Clerk-issued JWT. Per-role/
 * per-resource authorization (organizer owns this event, admin-only
 * actions, etc.) is deliberately NOT handled here — that mirrors the
 * existing Next.js app, where authentication and authorization are
 * separate concerns (see lib/auth/server.ts:
 * requireRole/requireEventOrganizer) and will be added per-endpoint in later
 * phases, not as a blanket rule here.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/api/v1/health").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/events", "/api/v1/events/**").permitAll()
                        // Phase G: the Razorpay webhook is a public,
                        // machine-to-machine endpoint — Razorpay never holds a
                        // Clerk JWT. Its own security is the X-Razorpay-Signature
                        // HMAC check (RazorpayWebhookSignatureVerifier), performed
                        // inside the controller itself, not here. Narrowly scoped
                        // to this exact POST path only — no broad /api/** exemption.
                        .requestMatchers(HttpMethod.POST, "/api/v1/webhooks/razorpay").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {
                }));

        return http.build();
    }
}
