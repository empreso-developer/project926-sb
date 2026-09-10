package com.project926.backend.service;

import com.project926.backend.dto.BannerUploadResponse;
import com.project926.backend.exception.BannerStorageException;
import com.project926.backend.exception.ForbiddenException;
import com.project926.backend.exception.InvalidBannerFileException;
import com.project926.backend.integration.supabase.SupabaseStorageGateway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Mirrors app/(project926)/p/api/upload-banner/route.ts's behavior —
 * mocked-collaborator coverage of authorization, validation, path
 * generation, and the storage-failure security improvement (no raw
 * message leaked). Real Supabase Storage behavior is proven separately by
 * the dev-DB-gated BannerUploadStorageIT.
 */
@ExtendWith(MockitoExtension.class)
class BannerUploadServiceTest {

    @Mock private ProfileService profileService;
    @Mock private SupabaseStorageGateway storageGateway;

    private BannerUploadService service() {
        return new BannerUploadService(profileService, storageGateway);
    }

    private static final String ORGANIZER_ID = "user_organizer00000000000";
    private static final String ADMIN_ID = "user_admin0000000000000000";
    private static final String CUSTOMER_ID = "user_customer000000000000";

    private MockMultipartFile jpeg() {
        return new MockMultipartFile("file", "banner.jpg", "image/jpeg", new byte[]{1, 2, 3});
    }

    // ---- authorization ----

    @Test
    void uploadBanner_organizer_succeeds() {
        when(storageGateway.publicUrl(eq("event-banners"), anyString()))
            .thenReturn("https://memdvuuszsistdjckcfp.supabase.co/storage/v1/object/public/event-banners/x.jpg");

        BannerUploadResponse result = service().uploadBanner(jpeg(), ORGANIZER_ID);

        verify(profileService).requireOrganizerOrAdminRole(ORGANIZER_ID);
        assertThat(result.url()).isEqualTo("https://memdvuuszsistdjckcfp.supabase.co/storage/v1/object/public/event-banners/x.jpg");
    }

    @Test
    void uploadBanner_admin_succeeds() {
        when(storageGateway.publicUrl(eq("event-banners"), anyString())).thenReturn("https://x/y.jpg");

        service().uploadBanner(jpeg(), ADMIN_ID);

        verify(profileService).requireOrganizerOrAdminRole(ADMIN_ID);
        verify(storageGateway).upload(eq("event-banners"), anyString(), any(byte[].class), eq("image/jpeg"));
    }

    @Test
    void uploadBanner_customer_throwsForbidden_neverTouchesStorage() {
        doThrow(new ForbiddenException("Requires organizer or admin role"))
            .when(profileService).requireOrganizerOrAdminRole(CUSTOMER_ID);

        assertThatThrownBy(() -> service().uploadBanner(jpeg(), CUSTOMER_ID))
            .isInstanceOf(ForbiddenException.class);

        verify(storageGateway, never()).upload(anyString(), anyString(), any(), anyString());
    }

    // ---- validation ----

    @Test
    void uploadBanner_missingFile_throwsInvalidBannerFile_withExactMessage() {
        assertThatThrownBy(() -> service().uploadBanner(null, ORGANIZER_ID))
            .isInstanceOf(InvalidBannerFileException.class)
            .hasMessage("No file provided");
    }

    @Test
    void uploadBanner_emptyFile_throwsInvalidBannerFile() {
        MockMultipartFile empty = new MockMultipartFile("file", "banner.jpg", "image/jpeg", new byte[0]);

        assertThatThrownBy(() -> service().uploadBanner(empty, ORGANIZER_ID))
            .isInstanceOf(InvalidBannerFileException.class)
            .hasMessage("No file provided");
    }

    @Test
    void uploadBanner_unsupportedMimeType_throwsInvalidBannerFile_withExactMessage() {
        MockMultipartFile pdf = new MockMultipartFile("file", "doc.pdf", "application/pdf", new byte[]{1});

        assertThatThrownBy(() -> service().uploadBanner(pdf, ORGANIZER_ID))
            .isInstanceOf(InvalidBannerFileException.class)
            .hasMessage("Unsupported file type");

        verify(storageGateway, never()).upload(anyString(), anyString(), any(), anyString());
    }

    @Test
    void uploadBanner_oversizedFile_throwsInvalidBannerFile_withExactMessage() {
        byte[] tooBig = new byte[(int) BannerUploadService.MAX_BYTES + 1];
        MockMultipartFile big = new MockMultipartFile("file", "banner.jpg", "image/jpeg", tooBig);

        assertThatThrownBy(() -> service().uploadBanner(big, ORGANIZER_ID))
            .isInstanceOf(InvalidBannerFileException.class)
            .hasMessage("File too large (max 5MB)");

        verify(storageGateway, never()).upload(anyString(), anyString(), any(), anyString());
    }

    // ---- accepted MIME types ----

    @Test
    void uploadBanner_validPng_succeeds() {
        MockMultipartFile png = new MockMultipartFile("file", "banner.png", "image/png", new byte[]{1});
        when(storageGateway.publicUrl(anyString(), anyString())).thenReturn("https://x/y.png");

        service().uploadBanner(png, ORGANIZER_ID);

        verify(storageGateway).upload(eq("event-banners"), anyString(), any(byte[].class), eq("image/png"));
    }

    @Test
    void uploadBanner_validWebp_succeeds() {
        MockMultipartFile webp = new MockMultipartFile("file", "banner.webp", "image/webp", new byte[]{1});
        when(storageGateway.publicUrl(anyString(), anyString())).thenReturn("https://x/y.webp");

        service().uploadBanner(webp, ORGANIZER_ID);

        verify(storageGateway).upload(eq("event-banners"), anyString(), any(byte[].class), eq("image/webp"));
    }

    @Test
    void uploadBanner_validGif_succeeds() {
        MockMultipartFile gif = new MockMultipartFile("file", "banner.gif", "image/gif", new byte[]{1});
        when(storageGateway.publicUrl(anyString(), anyString())).thenReturn("https://x/y.gif");

        service().uploadBanner(gif, ORGANIZER_ID);

        verify(storageGateway).upload(eq("event-banners"), anyString(), any(byte[].class), eq("image/gif"));
    }

    // ---- path generation ----

    @Test
    void uploadBanner_generatesFlatTimestampRandomPath_matchingExtension() {
        MockMultipartFile file = new MockMultipartFile("file", "photo.PNG", "image/png", new byte[]{1});
        when(storageGateway.publicUrl(anyString(), anyString())).thenReturn("https://x/y");

        service().uploadBanner(file, ORGANIZER_ID);

        ArgumentCaptor<String> pathCaptor = ArgumentCaptor.forClass(String.class);
        verify(storageGateway).upload(eq("event-banners"), pathCaptor.capture(), any(byte[].class), eq("image/png"));
        String path = pathCaptor.getValue();

        // "<millis>-<6 lowercase base36 chars>.<ext>", flat (no "/").
        assertThat(path).doesNotContain("/");
        assertThat(path).matches("\\d+-[0-9a-z]{6}\\.png");
    }

    @Test
    void uploadBanner_uppercaseExtension_isLowercased() {
        MockMultipartFile file = new MockMultipartFile("file", "banner.JPG", "image/jpeg", new byte[]{1});
        when(storageGateway.publicUrl(anyString(), anyString())).thenReturn("https://x/y");

        service().uploadBanner(file, ORGANIZER_ID);

        ArgumentCaptor<String> pathCaptor = ArgumentCaptor.forClass(String.class);
        verify(storageGateway).upload(eq("event-banners"), pathCaptor.capture(), any(byte[].class), eq("image/jpeg"));
        assertThat(pathCaptor.getValue()).endsWith(".jpg");
    }

    @Test
    void uploadBanner_noExtensionInFilename_defaultsToJpg() {
        MockMultipartFile file = new MockMultipartFile("file", "banner", "image/jpeg", new byte[]{1});
        when(storageGateway.publicUrl(anyString(), anyString())).thenReturn("https://x/y");

        service().uploadBanner(file, ORGANIZER_ID);

        ArgumentCaptor<String> pathCaptor = ArgumentCaptor.forClass(String.class);
        verify(storageGateway).upload(eq("event-banners"), pathCaptor.capture(), any(byte[].class), eq("image/jpeg"));
        assertThat(pathCaptor.getValue()).endsWith(".jpg");
    }

    // ---- storage failure: security-improvement (no raw message leaked) ----

    @Test
    void uploadBanner_storageGatewayThrows_propagatesBannerStorageException_notRawMessage() {
        doThrow(new BannerStorageException("Storage upload failed with status 403"))
            .when(storageGateway).upload(anyString(), anyString(), any(), anyString());

        assertThatThrownBy(() -> service().uploadBanner(jpeg(), ORGANIZER_ID))
            .isInstanceOf(BannerStorageException.class);
        // BannerStorageException is deliberately unmapped in
        // GlobalExceptionHandler (falls to the generic 500 handler, which
        // never echoes ex.getMessage() to the client) — verified at the
        // controller layer in BannerControllerTest.
    }
}
