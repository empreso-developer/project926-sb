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

    /**
     * Mirrors the Clerk webhook's {@code supabaseAdmin.from('profiles').upsert(profile, {onConflict:'id'})}
     * exactly: a single atomic INSERT ... ON CONFLICT DO UPDATE (native
     * SQL — JPQL has no upsert), so a genuinely-new user and a
     * re-delivered/duplicate event both converge on the identical final
     * row with no read-then-write race, matching this repository's
     * existing updateRole/BookingRepository's guarded-UPDATE convention.
     * {@code created_at}/{@code updated_at} are deliberately NOT set here
     * — the column DEFAULT now() handles a fresh INSERT, and the existing
     * trg_profiles_updated_at BEFORE UPDATE trigger handles the conflict
     * path, exactly as they already do for updateRole above.
     */
    @Modifying
    @Transactional
    @Query(value = "INSERT INTO profiles (id, email, first_name, last_name, role) "
            + "VALUES (:id, :email, :firstName, :lastName, :role) "
            + "ON CONFLICT (id) DO UPDATE SET "
            + "email = EXCLUDED.email, first_name = EXCLUDED.first_name, "
            + "last_name = EXCLUDED.last_name, role = EXCLUDED.role",
            nativeQuery = true)
    void upsertFromClerk(
            @Param("id") String id,
            @Param("email") String email,
            @Param("firstName") String firstName,
            @Param("lastName") String lastName,
            @Param("role") String role);

    /**
     * Mirrors the Clerk webhook's {@code supabaseAdmin.from('profiles').delete().eq('id', data.id)}
     * exactly: deleting a nonexistent id is a safe no-op (0 rows), unlike
     * {@link org.springframework.data.repository.CrudRepository#deleteById}
     * which throws EmptyResultDataAccessException when nothing matches —
     * this is what makes a duplicate/re-delivered user.deleted event safe.
     * Returns affected-row count, same pattern as updateRole.
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM Profile p WHERE p.id = :id")
    int deleteByIdSafe(@Param("id") String id);
}
