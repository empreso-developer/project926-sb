# Project926 Backend (Spring Boot)

Spring Boot backend for the Project926 event-ticketing platform, migrating
backend responsibilities off the existing Next.js app (`../app/(project926)`)
while keeping the same PostgreSQL/Supabase schema, the same business rules,
and Clerk as the sole identity provider. See the full migration audit and
phased plan discussed with the project owner for context — this README only
covers running what exists so far.

**Current status: Phase A only.** Project scaffold, dev database connection,
Clerk JWT authentication, health check. No business endpoints yet
(bookings/payments/inventory/QR/check-in/webhooks all still live in Next.js
and are untouched).

## Requirements

- Java 21 (`java -version`)
- Maven 3.9+ (a copy is not vendored; use your local `mvn`)
- Network access to the **development** Supabase Postgres instance and to
  Clerk's JWKS endpoint

**Windows note:** a JDK bug (`WEPollSelectorProvider` failing to open its
internal loopback pipe) has been observed preventing `java.net.http.HttpClient`
— and therefore the whole application, via `ResendGateway` — from starting
natively on some Windows machines, independent of JDK version (reproduced on
both 17 and 21). If you hit `Unable to establish loopback connection` on
startup, run the app under WSL2 (native Linux JDK) or Docker (below) instead
— both sidestep it entirely, since it's specific to the Windows NIO selector
implementation.

## Configuration

All configuration is environment variables — nothing is hardcoded, nothing
is committed. See [`.env.example`](.env.example) for the full list; copy it
to a local, gitignored file and load it into your shell or IDE run
configuration before starting the app.

**Database safety:** `SUPABASE_DB_HOST` etc. must point at the development
Supabase project (`memdvuuszsistdjckcfp.supabase.co`) confirmed with the
project owner — never the original production project. `spring.jpa.hibernate
.ddl-auto` is hardcoded to `validate` in `application.yml` and must stay that
way; it will never issue `CREATE`/`ALTER`/`DROP` against the database, only
fail startup if the JPA entities don't match the existing schema.

**Clerk:** `CLERK_JWK_SET_URI` / `CLERK_ISSUER` default (in
`application-dev.yml`) to this project's Clerk *development* instance,
derived from the same publishable key the Next.js app already uses in
`.env.local`. Override only if you're pointing at a different Clerk
environment.

## Running

```bash
cd backend
mvn spring-boot:run
```

Then:

```bash
curl http://localhost:8080/api/v1/health
# {"status":"UP","database":"UP"}   <- "database":"DOWN" means the datasource
#                                       env vars above aren't set/reachable

curl http://localhost:8080/api/v1/test/protected
# 401 — no token

curl -H "Authorization: Bearer <a real Clerk session JWT>" \
  http://localhost:8080/api/v1/test/protected
# {"clerkUserId":"user_...","clerkUserIdType":"String","message":"..."}
```

Get a real JWT for the last check by signing in to the Next.js app locally
and reading the session token Clerk issues (e.g. via `getToken()` in a
client component, or the Network tab), or via Clerk's dashboard test tools
for this dev instance.

## Tests

```bash
mvn test
```

- `SecurityConfigTest` (`@WebMvcTest`) — always runs, no external
  dependencies. Verifies the public health endpoint is open, the protected
  endpoint rejects missing/malformed tokens, and (via a locally-constructed
  test JWT, not a real Clerk round trip) that an authenticated request
  surfaces the Clerk subject claim as a `String`.
- `DatabaseConnectivityIT` (`@SpringBootTest`) — only runs when
  `SUPABASE_DB_PASSWORD` is set in the environment. Loads the full Spring
  context against the real dev database, which is also where Hibernate's
  `ddl-auto=validate` check actually happens: if the `Profile` entity didn't
  match the live `profiles` table, context startup would fail here. Skipped
  (not failed) when dev DB credentials aren't configured, so `mvn test`
  never needs real secrets to pass, and never touches a database it wasn't
  explicitly pointed at.

## Docker (local development only)

A multi-stage `Dockerfile` builds and runs the backend without needing a
local JDK/Maven at all. This is a **local development convenience**, not a
production deployment artifact — Supabase, Razorpay, Resend, and Clerk all
remain external managed services; nothing here stands up local replacements
for them.

**Architecture:** stage 1 compiles with `maven:3.9.9-eclipse-temurin-21`;
stage 2 runs the built jar on `eclipse-temurin:21-jre-alpine` as a non-root
user. Only the compiled jar is copied into the final image — no source, no
Maven cache, no `.env*` files (excluded from the build context entirely by
`.dockerignore`, so they can never end up in an image layer).

### Environment variables

Real values come from your existing `backend/.env.dev` (gitignored — see
[`.env.example`](.env.example) for the placeholder-only template), passed at
**runtime**, never baked into the image:

```bash
docker run --rm -p 8080:8080 --env-file .env.dev project926-backend:dev
```

**CRLF note:** `.env.dev` has Windows line endings. Unlike WSL2's native
`bash source` (which embeds a stray `\r` into every value and corrupts the
JDBC URL — see the CRLF/WSL investigation notes if you hit that), Docker's
`--env-file` / Compose's `env_file:` parser correctly strips CRLF — verified
empirically before relying on it here. No file changes or extra filtering
needed; just point `--env-file`/`env_file:` at `.env.dev` directly.

### Build and run

```bash
cd backend
docker build -t project926-backend:dev .
docker run -d --name project926-backend --env-file .env.dev -p 8080:8080 project926-backend:dev
```

Or with Compose (equivalent, less to type):

```bash
cd backend
docker compose up -d --build
```

### Verify it's running

```bash
docker ps                               # STATUS should show "healthy" after ~45s
curl http://localhost:8080/api/v1/health
# {"status":"UP","database":"UP"}
```

The container's `HEALTHCHECK` calls this same endpoint and greps the
response body for `"database":"UP"` — not just an HTTP 200 — since the
endpoint always returns 200 even when the database is unreachable (see
`HealthController`). A container stuck at `(unhealthy)` means it's up but
can't reach the configured database — check your `.env.dev` values, not the
container itself.

### Stop / remove

```bash
docker compose down          # if started via compose
# or
docker stop project926-backend && docker rm project926-backend
```

### Dev vs. production

`docker-compose.yml` and the `.env.dev`-based examples above are
**development only**. `application-prod.yml` remains the placeholder-only
file it's been since Phase A — no production Supabase/Razorpay/Resend/Clerk
credentials are wired up anywhere in this repo, and this Docker setup does
not change that. Building this image does not deploy anything.

## Project layout

```
src/main/java/com/p/backend/
  BackendApplication.java
  config/SecurityConfig.java       # stateless JWT resource-server config
  controller/HealthController.java         # public: GET /api/v1/health
  controller/ProtectedTestController.java  # protected: GET /api/v1/test/protected
  entity/Profile.java               # mirrors the existing `profiles` table
  repository/ProfileRepository.java
src/main/resources/
  application.yml       # common config; ddl-auto=validate lives here
  application-dev.yml   # dev Supabase + Clerk dev instance, env-var driven
  application-prod.yml  # placeholder only — not wired to real secrets yet
```

## What's deliberately NOT here yet

Bookings, ticket types, payments/Razorpay, inventory RPC calls, QR
generation, check-in, Resend email, the Razorpay/Clerk webhooks, and any
frontend changes. These land in later phases per the migration plan, each
gated on the project owner's approval before starting.
