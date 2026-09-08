package com.project926.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * A single shared {@link HttpClient} bean so ResendGateway's HTTP calls are
 * injectable (and therefore mockable in tests — Step 17: never call the
 * real Resend API from automated tests) rather than constructed inline.
 */
@Configuration
public class HttpClientConfig {

    @Bean
    public HttpClient httpClient() {
        return HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }
}
