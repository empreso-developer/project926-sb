package com.project926.backend.integration.supabase;

import com.project926.backend.exception.BannerStorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Mirrors lib/supabase/server.ts's {@code supabaseAdmin.storage} calls used
 * by the existing upload-banner route — the Storage REST API directly
 * (java.net.http.HttpClient, the same shared bean ResendGateway already
 * uses), not a Supabase SDK: no Supabase Java/Kotlin Storage SDK exists,
 * and one isn't warranted for two REST calls (upload, and the test-only
 * delete below).
 *
 * The service-role key authenticates every request (Authorization bearer
 * AND apikey header — Supabase's Storage API requires both), the same
 * credential class the existing Next.js supabaseAdmin client used, now
 * held only server-side in Spring instead of Next.js. Never logged, never
 * returned in any response.
 */
@Component
public class SupabaseStorageGateway {

    private static final Logger log = LoggerFactory.getLogger(SupabaseStorageGateway.class);

    private final HttpClient httpClient;
    private final String supabaseUrl;
    private final String serviceRoleKey;

    public SupabaseStorageGateway(
        HttpClient httpClient,
        @Value("${supabase.url:}") String supabaseUrl,
        @Value("${supabase.service-role-key:}") String serviceRoleKey
    ) {
        this.httpClient = httpClient;
        this.supabaseUrl = supabaseUrl == null ? "" : supabaseUrl.replaceAll("/+$", "");
        this.serviceRoleKey = serviceRoleKey;
    }

    /**
     * Mirrors {@code supabaseAdmin.storage.from(bucket).upload(path, file, {cacheControl:'3600', upsert:false, contentType})}
     * exactly: a single object-create call, never upsert (a colliding path
     * would fail — in practice impossible given the timestamp+random path
     * generation in BannerUploadService).
     */
    public void upload(String bucket, String path, byte[] content, String contentType) {
        requireConfigured();

        HttpRequest request = HttpRequest.newBuilder(objectUri(bucket, path))
            .timeout(Duration.ofSeconds(20))
            .header("Authorization", "Bearer " + serviceRoleKey)
            .header("apikey", serviceRoleKey)
            .header("Content-Type", contentType)
            .header("Cache-Control", "3600")
            .POST(HttpRequest.BodyPublishers.ofByteArray(content))
            .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            log.error("[supabase-storage] network error uploading to bucket={} path={}", bucket, path, e);
            throw new BannerStorageException("Could not reach storage service", e);
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            log.error("[supabase-storage] upload failed: bucket={} path={} status={} body={}",
                bucket, path, response.statusCode(), response.body());
            throw new BannerStorageException("Storage upload failed with status " + response.statusCode());
        }
    }

    /**
     * Mirrors {@code supabaseAdmin.storage.from(bucket).getPublicUrl(path)} —
     * a pure deterministic string construction (public bucket, no signed
     * URL), not a network call, so it cannot fail independently of the
     * upload above.
     */
    public String publicUrl(String bucket, String path) {
        return supabaseUrl + "/storage/v1/object/public/" + bucket + "/" + path;
    }

    /**
     * TEST-SUPPORT ONLY — public only so integration tests in a sibling
     * package can call it directly; NOT exposed via any controller or REST
     * endpoint anywhere in this application. Used exclusively by the
     * dev-DB-gated storage integration test to clean up the object it
     * uploads, mirroring this project's established "seed + @AfterEach
     * cleanup with residue assertion" pattern for every other integration
     * test, applied here to a storage object instead of a DB row.
     */
    public void delete(String bucket, String path) {
        requireConfigured();

        HttpRequest request = HttpRequest.newBuilder(objectUri(bucket, path))
            .timeout(Duration.ofSeconds(20))
            .header("Authorization", "Bearer " + serviceRoleKey)
            .header("apikey", serviceRoleKey)
            .DELETE()
            .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("[supabase-storage] test cleanup delete failed: bucket={} path={} status={}",
                    bucket, path, response.statusCode());
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            log.warn("[supabase-storage] test cleanup delete network error: bucket={} path={}", bucket, path, e);
        }
    }

    private URI objectUri(String bucket, String path) {
        return URI.create(supabaseUrl + "/storage/v1/object/" + bucket + "/" + path);
    }

    private void requireConfigured() {
        if (supabaseUrl.isBlank() || serviceRoleKey.isBlank()) {
            throw new BannerStorageException("SUPABASE_URL or SUPABASE_SERVICE_ROLE_KEY is not configured");
        }
    }
}
