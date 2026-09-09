# Project926 backend — multi-stage build.
#
# Stage 1 compiles the app with Maven + JDK 21 (matches pom.xml's
# <java.version>21</java.version> and spring-boot-starter-parent 3.3.5).
# Stage 2 ships ONLY the built jar on a minimal JRE — no Maven, no source,
# no build cache, no .env files (excluded from the build context entirely
# by .dockerignore, so they can never end up in an image layer).
#
# Tests are intentionally NOT run during this build (-DskipTests): running
# the dev-DB integration tests here would require injecting real Supabase
# credentials into the image build process, which is exactly what we don't
# want to do. The regression suite (`mvn test`, 216/216) is run separately,
# outside Docker, against the real dev database — see backend/README.md.

# ---- Stage 1: build ----
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /build

# Dependencies first, in their own layer, so a source-only change doesn't
# force re-downloading the whole dependency tree on every rebuild.
COPY pom.xml .
RUN mvn -q -B dependency:go-offline

COPY src ./src
RUN mvn -q -B package -DskipTests

# ---- Stage 2: runtime ----
FROM eclipse-temurin:21-jre-alpine

# Non-root user — no reason for this process to run as root.
RUN addgroup -S spring && adduser -S spring -G spring

WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
RUN chown spring:spring app.jar
USER spring:spring

# Documentation only (does not itself publish the port) — matches
# server.port's default in application.yml (${SERVER_PORT:8080}). If
# SERVER_PORT is overridden at runtime, adjust your `-p`/compose port
# mapping and the healthcheck URL below to match.
EXPOSE 8080

# GET /api/v1/health is public (SecurityConfig permits it without a Clerk
# token) and does a real round trip to the configured database
# (HealthController#health -> profileRepository.count()), so this is a
# genuine readiness check, not just "is the process alive" — the endpoint
# always returns HTTP 200 even when the database is down, so the check
# greps the response BODY for "database":"UP" rather than trusting the
# status code alone. Uses busybox's built-in wget/grep (already present in
# the alpine base) — no extra package installed just for this.
HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
  CMD wget -qO- http://127.0.0.1:8080/api/v1/health | grep -q '"database":"UP"' || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
