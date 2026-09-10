package com.project926.backend.security;

import com.project926.backend.config.SecurityConfig;
import com.project926.backend.controller.BannerController;
import com.project926.backend.dto.BannerUploadResponse;
import com.project926.backend.exception.BannerStorageException;
import com.project926.backend.exception.ForbiddenException;
import com.project926.backend.exception.GlobalExceptionHandler;
import com.project926.backend.exception.InvalidBannerFileException;
import com.project926.backend.service.BannerUploadService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.oauth2.jwt.JwtClaimNames.SUB;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers POST /api/v1/organizer/banners' HTTP-layer contract: 401 without a
 * token (unlike the existing Next.js route, which collapses this into 403
 * — see BannerUploadService's Javadoc for why this is a deliberate
 * improvement), 403 for a non-organizer/admin caller, 400 for validation
 * failures with the preserved message text, 500 with a GENERIC body (never
 * the raw storage error) on a gateway failure, and the exact
 * {@code {"url": "..."}} response shape with no service-role key anywhere
 * in it.
 */
@WebMvcTest(controllers = BannerController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class BannerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BannerUploadService bannerUploadService;

    private static final String CALLER_ID = "user_caller00000000000000";

    private MockMultipartFile jpeg() {
        return new MockMultipartFile("file", "banner.jpg", "image/jpeg", new byte[]{1, 2, 3});
    }

    @Test
    void uploadBanner_withoutToken_returns401() throws Exception {
        mockMvc.perform(multipart("/api/v1/organizer/banners").file(jpeg()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void uploadBanner_organizer_returns200_withExactUrlShape() throws Exception {
        when(bannerUploadService.uploadBanner(any(), org.mockito.ArgumentMatchers.eq(CALLER_ID)))
            .thenReturn(new BannerUploadResponse("https://memdvuuszsistdjckcfp.supabase.co/storage/v1/object/public/event-banners/123-abcdef.jpg"));

        mockMvc.perform(multipart("/api/v1/organizer/banners").file(jpeg())
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt -> jwt.subject(CALLER_ID).claim(SUB, CALLER_ID))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.url").value("https://memdvuuszsistdjckcfp.supabase.co/storage/v1/object/public/event-banners/123-abcdef.jpg"))
            // Response shape is exactly {url} — no other field, and
            // certainly no service-role key or any "key"/"secret" field.
            .andExpect(content().json("{\"url\":\"https://memdvuuszsistdjckcfp.supabase.co/storage/v1/object/public/event-banners/123-abcdef.jpg\"}", true));
    }

    @Test
    void uploadBanner_customer_forbiddenFromService_returns403() throws Exception {
        when(bannerUploadService.uploadBanner(any(), org.mockito.ArgumentMatchers.eq(CALLER_ID)))
            .thenThrow(new ForbiddenException("Requires organizer or admin role"));

        mockMvc.perform(multipart("/api/v1/organizer/banners").file(jpeg())
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt -> jwt.subject(CALLER_ID).claim(SUB, CALLER_ID))))
            .andExpect(status().isForbidden());
    }

    @Test
    void uploadBanner_missingFile_returns400() throws Exception {
        // No "file" part in the request at all — the @RequestParam binds
        // null (required=false, see BannerController's Javadoc), and the
        // REAL BannerUploadService.uploadBanner would throw
        // InvalidBannerFileException("No file provided") for a null file
        // (proven directly in BannerUploadServiceTest); mocked here since
        // this class mocks the service to isolate the HTTP-layer contract.
        when(bannerUploadService.uploadBanner(org.mockito.ArgumentMatchers.isNull(), anyString()))
            .thenThrow(new InvalidBannerFileException("No file provided"));

        mockMvc.perform(multipart("/api/v1/organizer/banners")
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt -> jwt.subject(CALLER_ID).claim(SUB, CALLER_ID))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("No file provided"));
    }

    @Test
    void uploadBanner_invalidFileFromService_returns400_withPreservedMessage() throws Exception {
        when(bannerUploadService.uploadBanner(any(), anyString()))
            .thenThrow(new InvalidBannerFileException("Unsupported file type"));

        mockMvc.perform(multipart("/api/v1/organizer/banners").file(jpeg())
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt -> jwt.subject(CALLER_ID).claim(SUB, CALLER_ID))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Unsupported file type"));
    }

    @Test
    void uploadBanner_storageGatewayFails_returns500_withGenericBody_neverRawMessage() throws Exception {
        when(bannerUploadService.uploadBanner(any(), anyString()))
            .thenThrow(new BannerStorageException("Storage upload failed with status 403: <supabase internal detail>"));

        mockMvc.perform(multipart("/api/v1/organizer/banners").file(jpeg())
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt -> jwt.subject(CALLER_ID).claim(SUB, CALLER_ID))))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.error").value("Internal server error"))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("supabase internal detail"))));
    }
}
