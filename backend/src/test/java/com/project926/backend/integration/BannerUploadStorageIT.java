package com.project926.backend.integration;

import com.project926.backend.dto.BannerUploadResponse;
import com.project926.backend.entity.Profile;
import com.project926.backend.exception.ForbiddenException;
import com.project926.backend.integration.supabase.SupabaseStorageGateway;
import com.project926.backend.repository.ProfileRepository;
import com.project926.backend.service.BannerUploadService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The mandatory real-Supabase-Storage proof for the banner-upload
 * migration: unlike RazorpayGateway/ResendGateway (mocked in every
 * integration test — Step 17 — since real calls would incur actual
 * payment/email side effects), SupabaseStorageGateway is exercised for
 * real here, the same way every other *IT class in this project exercises
 * the real dev Postgres database — Supabase Storage against the DEV
 * project (memdvuuszsistdjckcfp) carries no such side-effect risk and is
 * the only way to actually prove the upload/public-URL/delete contract
 * against the real Storage REST API.
 *
 * Uploads are tiny (a handful of bytes, not a real image) and deleted in
 * @AfterEach with an explicit residue check against the AUTHENTICATED
 * object endpoint (not the public URL, which sits behind Supabase's CDN
 * and can keep serving a cached 200 for a short window after a genuine
 * delete) — mirroring this project's "seed + cleanup + residue assertion"
 * convention, applied to a storage object instead of DB rows.
 *
 * Gated behind SUPABASE_DB_PASSWORD (this project's existing dev-DB-IT
 * gate — profiles must be seeded for authorization) AND requires
 * supabaseServiceRoleKey to actually be configured; targets ONLY the
 * dev project — never production.
 */
@SpringBootTest(
    classes = BannerUploadStorageIT.TestConfig.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@ActiveProfiles("dev")
@EnabledIfEnvironmentVariable(named = "SUPABASE_DB_PASSWORD", matches = ".+")
class BannerUploadStorageIT {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = Profile.class)
    @EnableJpaRepositories(basePackageClasses = ProfileRepository.class)
    @ComponentScan(basePackages = "com.project926.backend.repository")
    @Import({BannerUploadService.class, SupabaseStorageGateway.class, com.project926.backend.service.ProfileService.class})
    static class TestConfig {
        @Bean
        HttpClient httpClient() {
            return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        }
    }

    @Autowired
    private BannerUploadService bannerUploadService;

    @Autowired
    private SupabaseStorageGateway storageGateway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private HttpClient httpClient;

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.service-role-key}")
    private String supabaseServiceRoleKey;

    private final String organizerId = "user_it_banner_org_" + UUID.randomUUID().toString().substring(0, 8);
    private final String adminId = "user_it_banner_admin_" + UUID.randomUUID().toString().substring(0, 8);
    private final String customerId = "user_it_banner_cust_" + UUID.randomUUID().toString().substring(0, 8);
    private String uploadedPath;

    private void seedProfiles() {
        jdbcTemplate.update("INSERT INTO profiles (id, email, role) VALUES (?, ?, ?)",
            organizerId, organizerId + "@example.com", "organizer");
        jdbcTemplate.update("INSERT INTO profiles (id, email, role) VALUES (?, ?, ?)",
            adminId, adminId + "@example.com", "admin");
        jdbcTemplate.update("INSERT INTO profiles (id, email, role) VALUES (?, ?, ?)",
            customerId, customerId + "@example.com", "customer");
    }

    @AfterEach
    void cleanUp() {
        if (uploadedPath != null) {
            storageGateway.delete("event-banners", uploadedPath);
        }
        jdbcTemplate.update("DELETE FROM profiles WHERE id IN (?, ?, ?)", organizerId, adminId, customerId);

        Integer remainingProfiles = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM profiles WHERE id IN (?, ?, ?)", Integer.class, organizerId, adminId, customerId);
        assertThat(remainingProfiles).isZero();

        if (uploadedPath != null) {
            // Checked via the AUTHENTICATED object endpoint (same host as
            // upload/delete), not the public /object/public/ URL — the
            // public read path sits behind Supabase's CDN, which can keep
            // serving a cached 200 for a short window after a genuine
            // delete; the authenticated endpoint reflects storage's true
            // current state immediately.
            int status = getAuthenticatedStatus("event-banners", uploadedPath);
            assertThat(status).as("uploaded object must be gone after cleanup (no storage residue)").isNotEqualTo(200);
        }
    }

    private int getAuthenticatedStatus(String bucket, String path) {
        String url = supabaseUrl + "/storage/v1/object/" + bucket + "/" + path;
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + supabaseServiceRoleKey)
                .header("apikey", supabaseServiceRoleKey)
                .GET()
                .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String pathFromUrl(String url) {
        return url.substring(url.lastIndexOf('/') + 1);
    }

    private int getStatus(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).GET().build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void uploadBanner_organizer_realUploadSucceeds_publicUrlIsReachable() throws Exception {
        seedProfiles();
        MockMultipartFile file = new MockMultipartFile(
            "file", "it-banner.jpg", "image/jpeg", "not-a-real-jpeg-but-fine-for-storage".getBytes());

        BannerUploadResponse result = bannerUploadService.uploadBanner(file, organizerId);
        uploadedPath = pathFromUrl(result.url());

        assertThat(result.url()).startsWith("https://memdvuuszsistdjckcfp.supabase.co/storage/v1/object/public/event-banners/");
        assertThat(result.url()).endsWith(".jpg");

        int status = getStatus(result.url());
        assertThat(status).as("uploaded object should be publicly reachable immediately after upload").isEqualTo(200);
    }

    @Test
    void uploadBanner_admin_realUploadSucceeds() {
        seedProfiles();
        MockMultipartFile file = new MockMultipartFile(
            "file", "it-banner-admin.png", "image/png", "admin-upload-bytes".getBytes());

        BannerUploadResponse result = bannerUploadService.uploadBanner(file, adminId);
        uploadedPath = pathFromUrl(result.url());

        assertThat(result.url()).endsWith(".png");
        assertThat(getStatus(result.url())).isEqualTo(200);
    }

    @Test
    void uploadBanner_customer_forbidden_noObjectUploaded() {
        seedProfiles();
        MockMultipartFile file = new MockMultipartFile(
            "file", "it-banner.jpg", "image/jpeg", "bytes".getBytes());

        assertThatThrownBy(() -> bannerUploadService.uploadBanner(file, customerId))
            .isInstanceOf(ForbiddenException.class);
        // uploadedPath intentionally left null — nothing to clean up.
    }
}
