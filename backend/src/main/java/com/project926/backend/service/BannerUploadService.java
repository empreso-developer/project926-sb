package com.project926.backend.service;

import com.project926.backend.dto.BannerUploadResponse;
import com.project926.backend.exception.InvalidBannerFileException;
import com.project926.backend.integration.supabase.SupabaseStorageGateway;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.Set;

/**
 * Mirrors app/(project926)/p/api/upload-banner/route.ts exactly: organizer
 * or admin (ProfileService.requireOrganizerOrAdminRole — the same check
 * EventService.createEvent/listOwnEvents already use, not duplicated
 * here), same MIME/size limits, same flat bucket path scheme, same public
 * bucket. Deliberately NOT event-scoped — the existing route never
 * receives an event id (a banner can be uploaded while composing a
 * brand-new event, before it has an id), and this preserves that exactly.
 *
 * The upload itself never touches the `events` table — the returned URL
 * flows through the existing, already-migrated
 * createEventAction/updateEventAction -> Spring event endpoints exactly as
 * it did before this migration.
 */
@Service
public class BannerUploadService {

    static final long MAX_BYTES = 5L * 1024 * 1024;
    static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "image/gif");
    static final String BUCKET = "event-banners";

    private static final String BASE36 = "0123456789abcdefghijklmnopqrstuvwxyz";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ProfileService profileService;
    private final SupabaseStorageGateway storageGateway;

    public BannerUploadService(ProfileService profileService, SupabaseStorageGateway storageGateway) {
        this.profileService = profileService;
        this.storageGateway = storageGateway;
    }

    public BannerUploadResponse uploadBanner(MultipartFile file, String callerId) {
        profileService.requireOrganizerOrAdminRole(callerId);

        if (file == null || file.isEmpty()) {
            throw new InvalidBannerFileException("No file provided");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new InvalidBannerFileException("Unsupported file type");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new InvalidBannerFileException("File too large (max 5MB)");
        }

        String path = generatePath(file.getOriginalFilename());
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new InvalidBannerFileException("Could not read uploaded file");
        }

        storageGateway.upload(BUCKET, path, bytes, contentType);
        String url = storageGateway.publicUrl(BUCKET, path);
        return new BannerUploadResponse(url);
    }

    /**
     * Mirrors {@code `${Date.now()}-${Math.random().toString(36).slice(2, 8)}.${ext}`}
     * exactly: millisecond timestamp, 6 random lowercase base36 characters,
     * extension lowercased from the original filename (defaults to "jpg"
     * when absent) — flat, no per-user/per-event directory.
     */
    private static String generatePath(String originalFilename) {
        long timestamp = System.currentTimeMillis();
        StringBuilder rand = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            rand.append(BASE36.charAt(RANDOM.nextInt(BASE36.length())));
        }
        String ext = extractExtension(originalFilename);
        return timestamp + "-" + rand + "." + ext;
    }

    private static String extractExtension(String originalFilename) {
        if (originalFilename == null) return "jpg";
        int dot = originalFilename.lastIndexOf('.');
        if (dot < 0 || dot == originalFilename.length() - 1) return "jpg";
        return originalFilename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
