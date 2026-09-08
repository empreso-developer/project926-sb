package com.project926.backend.controller;

import com.project926.backend.repository.ProfileRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Public health endpoint. Separate from Actuator's /actuator/health so a
 * lightweight, unauthenticated liveness/DB-connectivity check exists without
 * exposing full actuator details.
 */
@RestController
public class HealthController {

    private final ProfileRepository profileRepository;

    public HealthController(ProfileRepository profileRepository) {
        this.profileRepository = profileRepository;
    }

    @GetMapping("/api/v1/health")
    public Map<String, Object> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");

        String dbStatus;
        try {
            // Cheapest real round trip to the configured database: counts
            // rows in the existing `profiles` table via the JPA mapping, so
            // this also implicitly exercises the entity mapping, not just
            // raw connectivity.
            profileRepository.count();
            dbStatus = "UP";
        } catch (Exception e) {
            dbStatus = "DOWN";
        }
        body.put("database", dbStatus);
        return body;
    }
}
