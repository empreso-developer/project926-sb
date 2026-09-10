package com.project926.backend.controller;

import com.project926.backend.dto.BannerUploadResponse;
import com.project926.backend.service.BannerUploadService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Mirrors app/(project926)/p/api/upload-banner/route.ts. Deliberately NOT
 * nested under /api/v1/organizer/events/** — the existing upload flow has
 * no event id at all (see BannerUploadService's Javadoc), so this is not
 * an event sub-resource. Authorization (organizer or admin, no event
 * ownership concept) is BannerUploadService's responsibility, reusing
 * EventService's own authorization dependency (ProfileService) rather
 * than duplicating it.
 */
@RestController
@RequestMapping("/api/v1/organizer")
public class BannerController {

    private final BannerUploadService bannerUploadService;

    public BannerController(BannerUploadService bannerUploadService) {
        this.bannerUploadService = bannerUploadService;
    }

    @PostMapping("/banners")
    public BannerUploadResponse uploadBanner(
        @AuthenticationPrincipal Jwt jwt,
        @RequestParam(name = "file", required = false) MultipartFile file
    ) {
        return bannerUploadService.uploadBanner(file, jwt.getSubject());
    }
}
