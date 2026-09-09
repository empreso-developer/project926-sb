package com.project926.backend.service;

import com.project926.backend.dto.AdminRevenueDto;
import com.project926.backend.dto.AdminUserDto;
import com.project926.backend.entity.Profile;
import com.project926.backend.exception.ForbiddenException;
import com.project926.backend.exception.ProfileNotFoundException;
import com.project926.backend.repository.BookingRepository;
import com.project926.backend.repository.ProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Mirrors the admin dashboard's three intentionally-hybrid Supabase reads
 * (see app/(project926)/p/dashboard/admin/page.tsx's Phase-H comment) and
 * lib/actions/admin.ts's updateProfileRoleAction. Like
 * EventModerationService, this defines its own local requireAdmin() rather
 * than depending on ProfileService — consistent with this codebase's
 * existing per-service admin-check convention.
 *
 * Revenue is deliberately computed from bookings.total_amount filtered on
 * bookings.status = 'confirmed' — NOT from the payments table or any
 * payment status — exactly matching the existing reduce() in the Next.js
 * page. Do not "improve" this to a payments-based calculation.
 */
@Service
public class AdminUserService {

    private final ProfileRepository profileRepository;
    private final BookingRepository bookingRepository;

    public AdminUserService(ProfileRepository profileRepository, BookingRepository bookingRepository) {
        this.profileRepository = profileRepository;
        this.bookingRepository = bookingRepository;
    }

    private void requireAdmin(String callerId) {
        boolean isAdmin = profileRepository.findById(callerId)
            .map(p -> "admin".equals(p.getRole()))
            .orElse(false);
        if (!isAdmin) {
            throw new ForbiddenException("Requires admin role");
        }
    }

    @Transactional(readOnly = true)
    public List<AdminUserDto> listAllUsers(String callerId) {
        requireAdmin(callerId);
        return profileRepository.findAllByOrderByCreatedAtDesc().stream()
            .map(this::toDto)
            .toList();
    }

    @Transactional(readOnly = true)
    public AdminRevenueDto getPlatformRevenue(String callerId) {
        requireAdmin(callerId);
        return new AdminRevenueDto(bookingRepository.sumConfirmedRevenue());
    }

    /**
     * Mirrors updateProfileRoleAction exactly, including its two
     * deliberately-preserved permissive behaviors: an admin MAY change
     * their own role (targetProfileId == callerId is not special-cased —
     * the existing AdminRoleSelect renders for the caller's own row too,
     * and no code in updateProfileRoleAction prevents self-modification),
     * and an admin MAY change another admin's role (no target-role
     * restriction exists in the original either). Neither is a privilege
     * escalation: only an already-fully-privileged admin can reach this
     * method at all.
     *
     * Role value validity is enforced by UpdateProfileRoleRequest's bean
     * validation before this is ever called. Target-existence is enforced
     * by the guarded UPDATE's affected-row count — a nonexistent
     * targetProfileId throws ProfileNotFoundException (404) instead of the
     * original's silent no-op success; see ProfileNotFoundException's
     * Javadoc for why this is a deliberate, documented hardening.
     */
    @Transactional
    public void updateUserRole(String targetProfileId, String newRole, String callerId) {
        requireAdmin(callerId);
        int updated = profileRepository.updateRole(targetProfileId, newRole);
        if (updated == 0) {
            throw new ProfileNotFoundException(targetProfileId);
        }
    }

    private AdminUserDto toDto(Profile profile) {
        return new AdminUserDto(
            profile.getId(),
            profile.getEmail(),
            profile.getFirstName(),
            profile.getLastName(),
            profile.getRole(),
            profile.getCreatedAt()
        );
    }
}
