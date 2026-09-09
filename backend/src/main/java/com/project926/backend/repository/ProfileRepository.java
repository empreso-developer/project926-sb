package com.project926.backend.repository;

import com.project926.backend.entity.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * {@code String} is the id type here to match {@link Profile#getId()} —
 * Clerk user ids, never UUID.
 */
public interface ProfileRepository extends JpaRepository<Profile, String> {

    /**
     * Mirrors the admin dashboard's Users table query ordering:
     * {@code .order('created_at', { ascending: false })} — see
     * app/(project926)/p/dashboard/admin/page.tsx.
     */
    List<Profile> findAllByOrderByCreatedAtDesc();

    /**
     * Mirrors updateProfileRoleAction's {@code .update({role}).eq('id', profileId)}
     * — a single guarded UPDATE, same "return affected rows" pattern as
     * BookingRepository's updateStatus/checkIn. {@code profiles.updated_at}
     * is bumped automatically by the existing trg_profiles_updated_at
     * database trigger, so no explicit updatedAt param is needed here.
     * Returns 0 when the target profile id doesn't exist, letting the
     * caller distinguish "updated" from "no such profile" without a
     * separate read.
     */
    @Modifying
    @Transactional
    @Query("UPDATE Profile p SET p.role = :role WHERE p.id = :id")
    int updateRole(@Param("id") String id, @Param("role") String role);
}
