package com.project926.backend.integration;

import com.project926.backend.dto.EventDto;
import com.project926.backend.entity.Profile;
import com.project926.backend.repository.ProfileRepository;
import com.project926.backend.service.EventService;
import com.project926.backend.service.ProfileService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase B's equivalent of Phase A's DatabaseConnectivityIT: exercises
 * ProfileService and EventService against the real DEVELOPMENT Supabase
 * Postgres database (memdvuuszsistdjckcfp — never production), proving the
 * Step 2/5/6/7 queries actually run correctly against the live schema, not
 * just against mocks.
 *
 * Same narrow-TestConfig approach as DatabaseConnectivityIT and for the
 * same reason: avoids booting BackendApplication's full component scan
 * (which would pull in SecurityConfig's HttpSecurity-requiring bean, unavailable
 * with webEnvironment=NONE). This test additionally imports only the two
 * specific services it needs (EventService, ProfileService) — NOT a
 * package-wide @ComponentScan of `service`, which would also sweep up
 * PaymentService (added in Phase D) and, transitively, its
 * BookingInventoryRepository/RazorpayGateway dependencies that this test
 * has no business constructing. Still never touches
 * `config`/`controller`/`security`.
 *
 * Deliberately does not assert on specific row counts/content: the dev
 * database has no seed data guarantee, so assertions are structural
 * (queries succeed, returned data is internally consistent) rather than
 * assuming particular events/profiles exist.
 *
 * Gated behind SUPABASE_DB_PASSWORD, same as DatabaseConnectivityIT.
 */
@SpringBootTest(
    classes = ProfileEventReadIT.TestConfig.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@ActiveProfiles("dev")
@EnabledIfEnvironmentVariable(named = "SUPABASE_DB_PASSWORD", matches = ".+")
class ProfileEventReadIT {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = Profile.class)
    @EnableJpaRepositories(basePackageClasses = ProfileRepository.class)
    @Import({EventService.class, ProfileService.class})
    static class TestConfig {
    }

    @Autowired
    private EventService eventService;

    @Autowired
    private ProfileService profileService;

    @Test
    void listApprovedEventsRunsAgainstRealDevSchema() {
        List<EventDto> events = eventService.listApprovedEvents();

        assertThat(events).isNotNull();
        // Whatever is there, it must actually be approved and ordered by
        // event_date ascending — the same guarantees the existing Next.js
        // query provides.
        assertThat(events).allSatisfy(e -> assertThat(e.status()).isEqualTo("approved"));
        for (int i = 1; i < events.size(); i++) {
            assertThat(events.get(i - 1).eventDate()).isBeforeOrEqualTo(events.get(i).eventDate());
        }
    }

    @Test
    void getEventByIdReturns404EquivalentForRandomId() {
        UUID randomId = UUID.randomUUID();
        assertThatThrownBy(() -> eventService.getEventById(randomId))
            .isInstanceOf(com.project926.backend.exception.EventNotFoundException.class);
    }

    @Test
    void profileLookupForUnknownClerkIdDefaultsToCustomer_realQuery() {
        String neverRegisteredClerkId = "user_definitelyNotInDevDb0";
        var response = profileService.getRoleForAuthenticatedUser(neverRegisteredClerkId);

        assertThat(response.role()).isEqualTo("customer");
    }
}
