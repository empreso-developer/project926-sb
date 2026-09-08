package com.project926.backend.repository;

import com.project926.backend.entity.Profile;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * {@code String} is the id type here to match {@link Profile#getId()} —
 * Clerk user ids, never UUID.
 */
public interface ProfileRepository extends JpaRepository<Profile, String> {
}
