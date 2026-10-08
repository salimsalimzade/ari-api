---
name: ari-api
description: Setup guide and roadmap for ari-api, the Java / Spring Boot backend of Ari, a website plus browser extension that saves highlights and clipped page sections with their sources. Use this skill whenever creating, configuring, running or extending anything in the ari-api repository (feature-module architecture, PostgreSQL with Liquibase and MyBatis, Spring Security sign-in, Gradle with GraalVM, google-java-format with Spotless, Error Prone, Lefthook git hooks, Docker), when choosing packages or folder structure, and when deciding what to build next on the backend, even if the request does not name this skill.
---

# ari-api: backend setup guide and roadmap

Last updated 2026-10-08. Ari ("arı" means "bee" in Azerbaijani) lives in two repositories:

- **ari-api** (this repo): the Java / Spring Boot backend.
- **ari**: the frontend (SolidStart website now, browser extension later). It calls this API from
  `http://localhost:3000`.

Write "Ari" in UI text and `ari` in code, package names and URLs; never use the dotless "ı" there.

Accounts are real: sign-in with a password or Google (F1, F9), stored in PostgreSQL with Liquibase
and MyBatis. Every new feature stores its data in PostgreSQL from the start; there is no in-memory
mode. Formatting, linting and CI run on every change. Not yet: queues, storage, Redis or billing.
Those are the TODO list in section 10, with the decisions already made. Diagrams and the sign-in
rules are in `docs/` (`architecture.md`, `sequences.md`, `database.md`).

## 1. What Ari does (context only)

The finished MVP lets users:

1. Save highlighted text from any web page with its source (title, site, URL, jump-back link).
2. Clip a whole page section (tables, code, lists) with its formatting.
3. Pull highlights out of PDF, Word and image files.
4. Browse, search, move, delete and export everything as Markdown, organized in folders.
5. Use it free up to 500 saves, or pay for Pro.

The library and import endpoints in section 5 come next, one feature at a time, each with its own
tables.

## 2. Commands that must work

| Command | What happens |
|---|---|
| `docker compose up -d postgres` | Starts PostgreSQL 18 on port 5432 for `bootRun` (data kept in the `postgres-data` volume) |
| `./gradlew bootRun` | Starts the API on http://localhost:8080; Liquibase creates or updates the tables |
| `./gradlew spotlessApply` | Formats all Java code (Google Java Style) and removes unused imports |
| `./gradlew check` | Format check + lint (Error Prone runs while compiling) + tests; Docker must be running (Testcontainers) |
| `docker compose up --build` | Runs PostgreSQL and the API in containers, the API on port 8080 |
| `lefthook install` | Installs the git hooks; run it once after cloning (section 6) |
| `cp .env.example .env` | Creates your local, git-ignored config; run it once after cloning (section 4) |
| GitHub Actions (`ci.yml`) | Runs format check, tests (with Error Prone lint) and build on every pull request and push to `main` (section 6) |

## 3. Packages

Java 25 on **GraalVM Community Edition**, Spring Boot 4.x, Gradle with the Groovy DSL
(`build.gradle`, `settings.gradle`), virtual threads on (`spring.threads.virtual.enabled=true`). Gradle project `ari-api`, base package `app.ari.api`, main
class `AriApiApplication`.

| Package | Why |
|---|---|
| `spring-boot-starter-webmvc` | REST controllers |
| `spring-boot-starter-validation` | Request validation |
| `spring-boot-starter-actuator` | `/actuator/health` |
| `spring-boot-starter-security-oauth2-resource-server` | Spring Security; our JWTs, read from the `ari_access` cookie or a Bearer header |
| `spring-boot-starter-security-oauth2-client` | Google sign-in |
| `spring-boot-starter-jdbc`, `mybatis-spring-boot-starter` (4.x for Boot 4), `postgresql` | Database access with SQL in XML mappers |
| `spring-boot-starter-liquibase` | Creates and updates the tables at startup |
| `springdoc-openapi-starter-webmvc-ui` (the version line for Boot 4) | Swagger UI and `/v3/api-docs` |
| `mapstruct` + `mapstruct-processor` | Mapping between domain models and request/response DTOs |
| `spring-boot-starter-test`, `spring-boot-starter-security-test`, `archunit-junit5` | Tests, plus the architecture rules test |
| `spring-boot-testcontainers`, `testcontainers-postgresql` | A real PostgreSQL 18 container for tests |
| Gradle plugins: `com.diffplug.spotless`, `net.ltgt.errorprone` | Formatting and linting (section 6) |

Not yet (TODO): AMQP, Redis, S3 SDK.

**JDK:** install GraalVM Community 25 on your machine. On Ubuntu with SDKMAN:
`sdk install java <latest 25.x>-graalce` (pick the newest one from `sdk list java`). IntelliJ IDEA
can also download it (Project Structure → SDKs → Download JDK → GraalVM Community). The Gradle
toolchain below requires a GraalVM 25 JDK, so the build never silently runs on another JDK.

## 4. Architecture and folder structure

**Style:** layered feature modules. Each aggregate is one folder under `feature/` with the same four
layers: presentation, application, domain, infrastructure. `feature/account` is the first built
module and shows every pattern (MyBatis adapters, Liquibase, exceptions, tests); `feature/highlight`
below is the planned layout to copy for library aggregates.

```text
ari-api/
├── build.gradle · settings.gradle · gradlew · gradle/
├── lefthook.yml · Dockerfile · docker-compose.yml · .editorconfig · .gitignore
├── .env.example                      # committed template; copy to .env (git-ignored)
├── .github/workflows/ci.yml          # CI: format → test → build (section 6)
├── docs/                             # Mermaid diagrams: architecture.md (server), sequences.md, database.md
├── .claude/skills/ari-api/SKILL.md     # this file
└── src/
    ├── main/java/app/ari/api/
    │   ├── AriApiApplication.java
    │   ├── common/                     # cross-feature shared code
    │   │   ├── client/                 # external clients and message publishers (later)
    │   │   ├── exception/              # base exceptions + GlobalExceptionHandler (Problem Details)
    │   │   ├── handlers/               # MyBatis TypeHandlers: Instant ↔ timestamptz in UTC, UUID ↔ uuid
    │   │   ├── validation/             # shared constraints, e.g. @MaxBytes
    │   │   ├── request/ · response/    # shared DTOs: cursor params, PageResponse<T>
    │   │   └── util/                   # small helpers: ids, time
    │   ├── config/                     # Spring config: security (JWT, Google), OpenAPI, CORS, Clock (later: RabbitMQ, storage)
    │   └── feature/                    # one folder per aggregate
    │       ├── highlight/              # ← reference module, copy this layout
    │       │   ├── presentation/       # HighlightController (interface + Swagger) + impl/HighlightControllerImpl
    │       │   ├── application/
    │       │   │   ├── facade/         # HighlightFacade + impl/HighlightFacadeImpl (controller → facade)
    │       │   │   ├── mapper/         # MapStruct mappers: domain ↔ request/response
    │       │   │   ├── model/          # DTOs: request/, response/
    │       │   │   └── exception/      # feature exceptions by type: notfound/, badrequest/
    │       │   ├── domain/
    │       │   │   ├── model/          # Highlight, HighlightKind
    │       │   │   ├── repository/     # HighlightRepository (port)
    │       │   │   └── service/        # HighlightService + impl/HighlightServiceImpl
    │       │   └── infrastructure/     # HighlightMyBatisRepository (@Mapper extending the port)
    │       ├── folder/                 # same layout as highlight
    │       ├── account/                # built: sign-in (password, Google), sessions, GET /me
    │       ├── fileimport/             # same layout; returns a fake finished job ("import" is a Java keyword)
    │       ├── outbox/                 # package-info.java only (TODO 5)
    │       └── util/                   # package-info.java only: storage helpers (TODO 4)
    ├── main/resources/
    │   ├── application.yml · application-dev.yml   # application-prod.yml later
    │   ├── db/changelog/               # Liquibase: master file + v1/ numbered files; never edit applied files
    │   ├── persistence/                # MyBatis XML mappers, e.g. UserMyBatisRepository.xml
    │   └── templates/                  # email templates (later)
    └── test/java/app/ari/api/          # mirrors main: ArchitectureTest + @IntegrationTest classes (support/)
```

Request flow: `XControllerImpl` → `XFacade` → `XService` → `XRepository` (port) → adapter in
`infrastructure/`.

Rules:

- **Presentation:** the `XController` interface holds the request mappings and Swagger annotations
  (`@Tag`, `@Operation`); `impl/XControllerImpl` is the `@RestController` and only calls the facade.
- **Facade:** orchestrates one use case and maps DTOs with MapStruct. No business rules here.
- **Service:** business rules. It talks only to the repository ports of its own feature, and
  returns `Optional` or `boolean` for "not possible" (email taken, wrong password).
- **Infrastructure:** adapters that implement the ports. A MyBatis adapter is a `@Mapper` interface
  that extends the port, with its SQL in `resources/persistence/<Name>.xml`; rows map into the
  domain records by constructor argument name. Unique keys guard data: inserts use
  `ON CONFLICT DO NOTHING` and return `false`. Tables come from a Liquibase changeset in the same
  commit.
- **Exceptions:** the facade turns "not possible" into feature exceptions, which extend the base
  classes in `common/exception` (`NotFoundException`, `BadRequestException`,
  `UnauthorizedException`, `ConflictException`) and sit in folders by type. `GlobalExceptionHandler`
  turns them into RFC 9457 Problem Details with a stable `code` (`NOT_FOUND`, `VALIDATION_FAILED`,
  `EMAIL_TAKEN`).
- **Transactions:** `@Transactional` on facade use cases, so a use case saves all or nothing.
- **Between features:** call the other feature's facade; never use its domain or infrastructure.
  `ArchitectureTest` (ArchUnit) checks this and the layer directions: presentation → application →
  domain, and infrastructure → domain only.
- Records for DTOs, constructor injection, an injected `Clock`, UUID ids, all times in UTC.
- **CORS:** allow the frontend's origin with credentials (the sign-in cookies), read from
  `app.cors.allowed-origins` in `application.yml`, which takes `APP_CORS_ALLOWED_ORIGINS`
  (comma-separated; default `http://localhost:3000`).
- **Sign-in:** every endpoint except `/api/v1/auth/**`, health and Swagger needs a signed-in user;
  read their id from `@AuthenticationPrincipal Jwt` (`jwt.getSubject()`). The cookie, token and
  Google rules are in `docs/architecture.md`.
- **Config and secrets:** secrets and values that differ per environment never go in the repo.
  `application.yml` refers to them as `${VAR:default}` placeholders (no default for real
  secrets). Locally they come from the git-ignored `.env`, which the `dev` profile imports
  (`optional:file:.env[.properties]` in `application-dev.yml`), so tests and CI never read it.
  Docker Compose passes it in with `env_file`. Real environment variables always win. The format is
  `KEY=value`, with no quotes and no `export`, so Spring and Compose read it the same way. List
  every key with a safe example value in `.env.example`.

## 5. API contract

Base path `/api/v1`, JSON, times as ISO-8601 in UTC, null fields left out of responses. The
frontend repo mirrors this contract, so keep `/v3/api-docs` accurate: the frontend will generate
its client from it later.

```java
public record MeResponse(UUID id, String email, String displayName, Plan plan, int savesCount, int savesLimit) {}
public record FolderResponse(UUID id, String name, int highlightsCount) {}
public record HighlightResponse(
    UUID id, UUID folderId, HighlightKind kind,
    String text, String contentMd,                  // contentMd only for CLIP
    String sourceTitle, String sourceSite, String sourceUrl,
    String fileName, Integer pageNumber, String jumpLink,
    Instant createdAt) {}
public record ImportJobResponse(UUID id, String fileName, ImportStatus status, int highlightsCount) {}
public record PageResponse<T>(List<T> items, String nextCursor) {}

enum Plan { FREE, PRO }
enum HighlightKind { TEXT, CLIP, FILE, IMAGE }
enum ImportStatus { QUEUED, PROCESSING, DONE, FAILED }
```

| Endpoint | Feature | Behavior |
|---|---|---|
| `POST /auth/register` `{ email, password, displayName }` | `account` | 201 + `MeResponse` + both cookies; 409 `EMAIL_TAKEN` |
| `POST /auth/login` `{ email, password }` | `account` | 200 + `MeResponse` + both cookies; 401 `INVALID_CREDENTIALS` |
| `POST /auth/refresh` (`ari_refresh` cookie) | `account` | 204 + new cookies; 401 clears both |
| `POST /auth/logout` (`ari_refresh` cookie) | `account` | 204, ends the session and clears both cookies |
| `GET /me` | `account` | The signed-in user (plan `FREE`, `savesLimit` 500); 401 when signed out |
| `GET /oauth2/authorization/google` | `account` | Google sign-in (a full-page link, not `fetch`) |
| `GET /folders` | `folder` | Seeded folders, e.g. "Spring Boot" and "Thesis" |
| `POST /folders` `{ name }` | `folder` | Adds a folder in memory |
| `GET /highlights?folderId=&q=&cursor=` | `highlight` | Filters the in-memory list (case-insensitive match on text and title), 50 per page |
| `POST /highlights` | `highlight` | Adds an item and returns it with `id` and `createdAt` |
| `DELETE /highlights/{id}` | `highlight` | Removes it |
| `POST /imports` (multipart `file`) | `fileimport` | Ignores the file content; returns a `DONE` job with 0 highlights |
| `GET /imports/{id}` | `fileimport` | Returns that job |

Seed 2 folders and 6 highlights covering every `kind`, including one `CLIP` with a Markdown table and
one `FILE` with a page number, so the frontend shows each case.

## 6. Formatting, linting and CI (on commit, push and pull request)

Code is formatted on every commit, checked before every push, and checked again in GitHub Actions,
so unformatted or failing code never reaches a branch or gets merged.

| Concern | Tool and rule |
|---|---|
| Formatting | google-java-format through Spotless: Google Java Style, 2-space indent, 100 columns |
| Clean imports | Spotless removes unused imports, google-java-format sorts them, and `import x.*` fails the check |
| Annotations | Spotless `formatAnnotations()` keeps type annotations on the same line |
| Lint | Error Prone runs while compiling. These checks fail the build: `UnusedVariable`, `UnusedMethod`, `MissingOverride`, `ReferenceEquality`, `DefaultCharset` |

`build.gradle` (sketch; add the latest version to each plugin and library):

```groovy
plugins {
  id 'java'
  id 'org.springframework.boot'
  id 'io.spring.dependency-management'
  id 'com.diffplug.spotless'
  id 'net.ltgt.errorprone'
}

java {
  toolchain {
    languageVersion = JavaLanguageVersion.of(25)
    vendor = JvmVendorSpec.GRAAL_VM
  }
}

spotless {
  java {
    target 'src/**/*.java'
    googleJavaFormat()
    removeUnusedImports()
    forbidWildcardImports()
    formatAnnotations()
  }
}

dependencies {
  errorprone 'com.google.errorprone:error_prone_core'   // add the latest version
}

tasks.withType(JavaCompile).configureEach {
  options.errorprone {
    disableWarningsInGeneratedCode = true               // skip MapStruct output
    excludedPaths = '.*/build/generated/.*'
    error 'UnusedVariable', 'UnusedMethod', 'MissingOverride', 'ReferenceEquality', 'DefaultCharset'
  }
}
```

Spotless hooks `spotlessCheck` into `./gradlew check`, so `check` covers format, lint and tests.

google-java-format is pinned to 1.36.1: in 1.37.0 `JavaFormatterOptions.Style` became a record, and
Spotless 8.10.3 still calls `Style.valueOf`, so every file fails with `InvocationTargetException`.
Unpin it once a Spotless release supports 1.37+.

Git hooks with Lefthook. Install Lefthook once on your machine (on Ubuntu with Volta:
`volta install lefthook`), then run `lefthook install` in the repo after cloning.

```yaml
# lefthook.yml
pre-commit:
  commands:
    format:
      glob: "*.java"
      run: ./gradlew spotlessApply --quiet
      stage_fixed: true
pre-push:
  commands:
    check:
      run: ./gradlew spotlessCheck test --quiet
```

- **pre-commit:** formats the Java code and removes unused imports; `stage_fixed` adds the fixes
  to the same commit.
- **pre-push:** the push stops if anything is unformatted, has a wildcard import, fails an Error
  Prone check (it runs while `test` compiles) or fails a test.

**IntelliJ IDEA:** install the google-java-format plugin and enable it, so the editor formats exactly
like the hook. `.editorconfig` sets 2-space indentation.

### CI (GitHub Actions)

`.github/workflows/ci.yml` runs the same checks as the hooks on every pull request and every push
to `main`, as separate steps so a failure shows exactly what broke: format, test, build. Error
Prone lint runs while `test` compiles, so it needs no step of its own. Keep the workflow free of
secrets and per-environment values: tests use the `test` profile (`src/test/resources/
application-test.yml`, fake values only) and start their own PostgreSQL with Testcontainers; GitHub's
runners have Docker.

```yaml
# .github/workflows/ci.yml (use the latest major version of each action)
name: CI

on:
  pull_request:
  push:
    branches: [main]

permissions:
  contents: read

concurrency:
  group: ci-${{ github.ref }}
  cancel-in-progress: true

jobs:
  check:
    name: Format, test, build
    runs-on: ubuntu-latest
    steps:
      - name: Checkout
        uses: actions/checkout@v6
      - name: Set up GraalVM 25
        uses: graalvm/setup-graalvm@v1
        with:
          distribution: graalvm-community
          java-version: '25'
          cache: gradle
      - name: Format
        run: ./gradlew spotlessCheck
      - name: Test
        run: ./gradlew test
      - name: Build
        run: ./gradlew bootJar
```

- Commit `gradlew` as executable (`git update-index --chmod=+x gradlew`), or CI fails on the first
  step that calls it.
- In the repo's branch protection settings, make the `check` job required for `main`, so nothing
  merges unless format, test and build pass.

## 7. Docker (local only)

- `Dockerfile`: two stages, both on `ghcr.io/graalvm/jdk-community:25` (GraalVM publishes JDK
  images, not JRE-only ones). The build stage runs `./gradlew bootJar`; the runtime stage copies
  the jar and runs it on port 8080. If the wrapper fails with "xargs: command not found", install
  `findutils` in the build stage (`microdnf install -y findutils`).
- `docker-compose.yml`: `postgres` (`postgres:18`, user, password and database from `.env`, data in
  the `postgres-data` volume mounted at `/var/lib/postgresql`, the path PostgreSQL 18 expects, and a
  `pg_isready` healthcheck) and `api`, which waits for a healthy `postgres` and reaches it at
  `jdbc:postgresql://postgres:5432/ari`. For `./gradlew bootRun`, start only the database:
  `docker compose up -d postgres`.

Later, add `redis`, `rabbitmq`, `seaweedfs` and `mailpit` services here as their TODO items start.

## 8. Rules for the AI assistant

- Build only what this file describes. No queues, storage, Redis or billing code until the matching
  TODO item starts.
- For every new aggregate, copy the layout of `feature/highlight` exactly, and the patterns of
  `feature/account` (MyBatis adapter, Liquibase changeset, exceptions, `@IntegrationTest`).
- Tests that need the database use `@IntegrationTest` (`src/test/java/app/ari/api/support`): one
  shared Spring context on a PostgreSQL container, so give every test its own data (for example a
  random email).
- Create folders when their first code arrives. Each feature folder holds only
  `package-info.java` until its issue starts.
- Any change to an endpoint or response shape must be reflected in `/v3/api-docs` and called out,
  because the frontend repo depends on it.
- Formatting and linting run through the Lefthook git hooks. Never skip them (`--no-verify` or
  `LEFTHOOK=0`).
- Keep CI green. If you change a check, change it in `lefthook.yml` and `ci.yml` together.
- Never hardcode a secret or a per-environment value. Add it to `.env.example` and read it with a
  `${VAR}` placeholder (section 4). Never commit `.env`.
- Prefer simple, explicit code. Ask before adding any package not listed here.
- If unsure about an API, check current docs for Spring Boot 4.x, GraalVM, Spotless, Error Prone
  and Lefthook.
- When a decision changes, update this file in the same commit.

## 9. Phase 1 is done when

- `./gradlew bootRun` starts the API; http://localhost:8080/swagger-ui.html lists every endpoint,
  and `/actuator/health` returns UP.
- The endpoints in section 5 return the seeded data. Creating a folder and adding or deleting a
  highlight survive a restart.
- Committing a badly formatted Java file formats it automatically; a push with a wildcard import or
  an unused variable is blocked.
- `docker compose up --build` serves the same API on port 8080.
- `./gradlew check` passes, including `ArchitectureTest`.
- On GitHub, a pull request shows the `CI` workflow passing: format check, test and build.

## 10. TODO: next requirements (decisions already made)

Do these in order, one at a time:

1. **Database (done for accounts):** PostgreSQL 18 + MyBatis + Liquibase, set up with F1. Each
   repository port gets a MyBatis adapter in `infrastructure/` (e.g. `HighlightMyBatisRepository`)
   with its XML mapper in `resources/persistence/HighlightMyBatisRepository.xml`. TypeHandlers in
   `common/handlers` store `Instant` as UTC. Migrations in `resources/db/changelog/`: a master file
   plus numbered files in `v1/`; never edit an applied file. No in-memory or `mock` mode: the
   frontend runs PostgreSQL with docker compose. Keep a `users.saves_count` counter for the plan
   limit. Each library feature adds its own tables (see `docs/database.md`).
2. **Auth (done, F1 and F9):** Spring Security; our own JWT access tokens (15 minutes) plus rotating
   refresh tokens (httpOnly cookies for the website); Google sign-in through OAuth2 Client; email +
   password with bcrypt. Rules in `docs/architecture.md`.
3. **Search:** Postgres full-text search: a generated `tsvector` column with the `simple` config, an
   IMMUTABLE wrapper around `unaccent`, a GIN index, and `pg_trgm` for short or partial queries.
   Added as a Liquibase changeset.
4. **File imports:** storage helpers in `feature/util` talk to S3-compatible storage (SeaweedFS
   locally, Cloudflare R2 later; not MinIO, whose community edition is archived). Flow: upload →
   background job → PDFBox reads PDF highlight annotations with page numbers, POI reads DOCX
   highlights and comments → delete the file. Image OCR happens in the browser (frontend repo).
   EPUB is not supported.
5. **Async jobs and retries:** a transactional outbox in `feature/outbox`. Write the outbox row in
   the same transaction as the business change; a scheduled publisher sends pending rows to RabbitMQ
   (publishers in `common/client`) and marks them sent. Consumers retry 2 s → 10 s → 60 s, then go to
   a dead-letter queue; they are idempotent by event id; failed jobs are visible to the user.
   Queues: `import.process`, `email.transactional`, `email.bulk`.
6. **Redis:** rate limits, one-time codes (including the browser extension's sign-in code) and
   idempotency keys only.
7. **Billing:** Paddle Billing webhooks. Free = 500 saves; Pro = unlimited, $6/month or $48/year, the
   same price everywhere. Verify webhook signatures and ignore duplicate events.
8. **API contract for the frontend:** keep springdoc's OpenAPI complete (every endpoint, field and
   error code), since the frontend generates its typed client from `/v3/api-docs`.
9. **Native image (optional):** compile ari-api to a GraalVM native executable with the
   `org.graalvm.buildtools.native` plugin (`./gradlew nativeCompile`, images from
   `ghcr.io/graalvm/native-image-community:25`) for much faster startup and lower memory. Builds
   take minutes and need a lot of RAM, and MyBatis XML mappers need reflection hints, so decide
   after the database step.
10. **Hosting:** only after everything works locally; a deploy workflow joins `ci.yml` then.
