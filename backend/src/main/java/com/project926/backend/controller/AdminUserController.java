package com.project926.backend.controller;

import com.project926.backend.dto.AdminRevenueDto;
import com.project926.backend.dto.AdminUserDto;
import com.project926.backend.dto.UpdateProfileRoleRequest;
import com.project926.backend.service.AdminUserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Mirrors the admin dashboard's Users table, Revenue stat, and
 * AdminRoleSelect's updateProfileRoleAction (see AdminUserService's
 * Javadoc for the full behavioral audit). Admin-only, enforced in
 * AdminUserService — the authenticated identity always comes from the
 * validated JWT subject; the target profile id for role updates comes only
 * from the path, never treated as an authorization claim.
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping("/users")
    public List<AdminUserDto> listAllUsers(@AuthenticationPrincipal Jwt jwt) {
        return adminUserService.listAllUsers(jwt.getSubject());
    }

    @GetMapping("/revenue")
    public AdminRevenueDto getPlatformRevenue(@AuthenticationPrincipal Jwt jwt) {
        return adminUserService.getPlatformRevenue(jwt.getSubject());
    }

    @PatchMapping("/users/{profileId}/role")
    public ResponseEntity<Void> updateUserRole(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable String profileId,
        @Valid @RequestBody UpdateProfileRoleRequest request
    ) {
        adminUserService.updateUserRole(profileId, request.role(), jwt.getSubject());
        return ResponseEntity.noContent().build();
    }
}
