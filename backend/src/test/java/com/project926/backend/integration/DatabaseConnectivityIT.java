package com.project926.backend.integration;

import com.project926.backend.entity.Profile;
import com.project926.backend.repository.ProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full-context integration test against the real DEVELOPMENT Supabase
 * Postgres database (memdvuuszsistdjckcfp — never production).
 *
 * This is the test that actually proves three of Phase A's requirements at
 * once, because all three only mean something against a live connection:
 *   1. the datasource in application-dev.yml successfully connects,
 *   2. spring.jpa.hibernate.ddl-auto=validate passes against the existing
 *      `profiles` table (if the Profile entity mapping didn't match the
 *      live schema, context startup itself would fail here), and
 *   3. Hibernate made no schema changes — validate mode is physically
 *      incapable of issuing DDL, so a passing context load is the proof.
 *
 * Deliberately does NOT boot {@code BackendApplication} (which
 * component-scans the whole {@code com.project926.backend} package,
 * including SecurityConfig). SecurityConfig's SecurityFilterChain bean
 * requires an {@code HttpSecurity} instance, which Spring Security only
 * creates for a servlet web application context — never in a
 * {@code webEnvironment = NONE} test, regardless of which autoconfiguration
 * classes are excluded, because the bean is defined unconditionally on our
 * own {@code @Configuration} class rather than gated behind
 * {@code @ConditionalOnWebApplication} the way Spring Boot's own security
 * autoconfiguration is. This test isn't testing security at all, so instead
 * of touching SecurityConfig (or the app's security behavior) to work
 * around that, it boots a narrow, test-local configuration that only turns
 * on autoconfiguration plus the specific entity/repository packages needed
 * to prove DB connectivity and schema validation — it never scans
 * {@code com.project926.backend.config} (or .security/.controller), so
 * SecurityConfig is simply never a candidate bean here.
 *
 * Gated behind SUPABASE_DB_PASSWORD so `mvn test` never fails (or silently
 * hits a real database) in an environment that hasn't configured the dev
 * Supabase credentials — it's skipped, not red, when they're absent.
 */
@SpringBootTest(
    classes = DatabaseConnectivityIT.TestConfig.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@ActiveProfiles("dev")
@EnabledIfEnvironmentVariable(named = "SUPABASE_DB_PASSWORD", matches = ".+")
class DatabaseConnectivityIT {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = Profile.class)
    @EnableJpaRepositories(basePackageClasses = ProfileRepository.class)
    static class TestConfig {
    }

    @Autowired
    private ConfigurableApplicationContext context;

    @Autowired
    private ProfileRepository profileRepository;

    @Test
    void applicationContextStartsAndConnectsToDevSupabase() {
        // The context is only reachable here at all if Spring Boot finished
        // starting it — which for this TestConfig means the datasource
        // connected AND Hibernate's validate-mode schema check against the
        // live `profiles` table already passed (see class Javadoc). This is
        // the explicit "context started successfully" assertion requested;
        // the query below is the actual round trip over that connection.
        assertThat(context.isActive()).isTrue();

        long count = profileRepository.count();
        assertThat(count).isGreaterThanOrEqualTo(0);
    }
}
