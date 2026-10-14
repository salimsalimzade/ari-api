---
name: ari-api
description: Setup guide and roadmap for ari-api, the Java / Spring Boot backend of Ari, a website plus browser extension that saves highlights and clipped page sections with their sources. Use this skill whenever creating, configuring, running or extending anything in the ari-api repository (feature-module architecture, PostgreSQL with Liquibase and MyBatis, Spring Security sign-in, Gradle with GraalVM, google-java-format with Spotless, Error Prone, Lefthook git hooks, Docker), when choosing packages or folder structure, and when deciding what to build next on the backend, even if the request does not name this skill.
---

# ari-api: backend setup guide and roadmap

Last updated 2026-10-15. Ari ("arı" means "bee" in Azerbaijani) lives in two repositories:

- **ari-api** (this repo): the Java / Spring Boot backend.
- **ari**: the frontend (SolidStart website now, browser extension later). It calls this API from
  `http://localhost:3000`.

Write "Ari" in UI text and `ari` in code, package names and URLs; never use the dotless "ı" there.

Accounts are real: sign-in with a password (F1), stored in PostgreSQL with Liquibase
and MyBatis. Every new feature stores its data in PostgreSQL from the start; there is no in-memory
mode. Formatting, linting and CI run on every change. Not yet: billing.
Those are the TODO list in section 10, with the decisions already made. Diagrams and the sign-in
rules are in `docs/` (`architecture.md`, `sequences.md`, `database.md`).

## 1. What Ari does (context only)

The finished MVP lets users:

1. Save highlighted text from any web page with its source (title, site, URL; the website builds the
   jump-back link from the URL and the text).
2. Clip a whole page section (tables, code, lists) with its formatting.
3. Pull highlights out of PDF, Word and image files.
4. Browse, search, tag, filter, delete and export everything as Markdown.
5. Try everything free for 14 days once a card is added, then pay for Hive (the only plan).

The library and import endpoints in section 5 come next, one feature at a time, each with its own
tables.

## 2. Commands that must work

| Command | What happens |
|---|---|
| `docker compose up -d postgres redis rabbitmq mailpit minio` | Starts what `bootRun` needs: PostgreSQL 18 on 5432 (data kept in the `postgres-data` volume), Redis 8 on 6379, RabbitMQ 4 on 5672 (admin page on http://localhost:15672), Mailpit (SMTP on 1025, inbox on http://localhost:8025) and MinIO (S3 API on 9000, console on http://localhost:9001, files in the `minio-data` volume) |
| `./gradlew bootRun` | Starts the API on http://localhost:8080; Liquibase creates or updates the tables |
| `./gradlew spotlessApply` | Formats all Java code (Google Java Style) and removes unused imports |
| `./gradlew check` | Format check + lint (Error Prone runs while compiling) + tests; Docker must be running (Testcontainers) |
| `docker compose up --build` | Runs PostgreSQL and the API in containers, the API on port 8080 |
| `lefthook install` | Installs the git hooks; run it once after cloning (section 6) |
| `cp .env.example .env` | Creates your local, git-ignored config with every key empty; run it once after cloning, then fill in the values (section 4) |
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
| `spring-boot-starter-jdbc`, `mybatis-spring-boot-starter` (4.x for Boot 4), `postgresql` | Database access with SQL in XML mappers |
| `spring-boot-starter-liquibase` | Creates and updates the tables at startup |
| `spring-boot-starter-data-redis` | Verify-link codes and rate limits (`StringRedisTemplate`); tests run a `redis:8` container |
| `spring-boot-starter-amqp`, `testcontainers-rabbitmq` | The outbox publisher (publisher confirms) and the consumers; tests run a `rabbitmq:4` container |
| `software.amazon.awssdk:s3` (BOM `awsSdkVersion`, without the Netty async client), `testcontainers-minio` | `ObjectStorage`: files in MinIO through the S3 API, signed links for images; tests run the same MinIO image |
| `pdfbox` (3.x), `poi-ooxml` (5.x) | The import consumer's readers: PDF annotations (and pictures of drawn marks), Word highlights and comments |
| `spring-boot-starter-mail`, `spring-boot-starter-thymeleaf` | The mail sender: SMTP, and the email templates (HTML plus a plain-text version); tests catch emails in an `axllent/mailpit` container |
| `springdoc-openapi-starter-webmvc-ui` (the version line for Boot 4) | Swagger UI and `/v3/api-docs` |
| `mapstruct` + `mapstruct-processor` | Mapping between domain models and request/response DTOs |
| `spring-boot-starter-test`, `spring-boot-starter-security-test`, `archunit-junit5` | Tests, plus the architecture rules test |
| `spring-boot-testcontainers`, `testcontainers-postgresql` | A real PostgreSQL 18 container for tests |
| Gradle plugins: `com.diffplug.spotless`, `net.ltgt.errorprone` | Formatting and linting (section 6) |


**JDK:** install GraalVM Community 25 on your machine. On Ubuntu with SDKMAN:
`sdk install java <latest 25.x>-graalce` (pick the newest one from `sdk list java`). IntelliJ IDEA
can also download it (Project Structure → SDKs → Download JDK → GraalVM Community). The Gradle
toolchain below requires a GraalVM 25 JDK, so the build never silently runs on another JDK.

## 4. Architecture and folder structure

**Style:** layered feature modules. Each aggregate is one folder under `feature/` with the same four
layers: presentation, application, domain, infrastructure. `feature/account` is the first built
module and shows every pattern (MyBatis adapters, Liquibase, exceptions, tests); `feature/save`
is the reference for library aggregates (it also stores files and calls other features' facades).

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
    │   │   ├── client/                 # MessagePublisher (RabbitMQ, waits for its confirm), ObjectStorage (MinIO through S3)
    │   │   ├── exception/              # base exceptions + GlobalExceptionHandler (Problem Details)
    │   │   ├── handlers/               # MyBatis TypeHandlers: Instant ↔ timestamptz in UTC, UUID ↔ uuid
    │   │   ├── image/                  # ImageFormat: JPEG, PNG or WebP, recognized by the first bytes
    │   │   ├── ratelimit/              # RateLimiter: fixed-window counters in Redis
    │   │   ├── validation/             # shared constraints, e.g. @MaxBytes
    │   │   ├── response/               # shared DTOs: PageResponse<T>
    │   │   ├── transaction/            # AfterCommit: work that must wait for the commit
    │   │   └── util/                   # small helpers: ids, time
    │   ├── config/                     # Spring config: security (JWT), OpenAPI, CORS, Clock, RabbitMQ queues, scheduling, mail (later: storage)
    │   └── feature/                    # one folder per aggregate
    │       ├── save/                   # built, the reference module: the Library (highlights and clips) and its tags
    │       │   ├── presentation/       # SaveController (interface + Swagger) + impl/SaveControllerImpl; uses application types only
    │       │   ├── application/
    │       │   │   ├── facade/         # SaveFacade + impl/SaveFacadeImpl (controller → facade; calls SourceFacade and AccountFacade)
    │       │   │   ├── mapper/         # MapStruct mappers: domain ↔ request/response
    │       │   │   ├── model/          # DTOs: request/, response/
    │       │   │   └── exception/      # feature exceptions by type: notfound/, badrequest/
    │       │   ├── domain/
    │       │   │   ├── model/          # Save, NewSave, SaveKind, ClipType, HighlightColor, Tag, SaveFilter, SaveCursor
    │       │   │   ├── repository/     # SaveRepository, TagRepository (ports)
    │       │   │   └── service/        # SaveService + impl/SaveServiceImpl (also stores image clips through ObjectStorage)
    │       │   └── infrastructure/     # SaveMyBatisRepository, TagMyBatisRepository (@Mapper extending the ports)
    │       ├── source/                 # built: the page or file a save came from, and the Sources page (GET /sources); its SQL reads the saves and notes tables, never writes them
    │       ├── note/                   # built: the Notes page (notes, note_blocks); quotes go through SaveFacade
    │       ├── account/                # built: sign-in (password), sessions, the extension's sign-in code, GET /me
    │       ├── mail/                   # listener → facade → MailService; ports for Thymeleaf and SMTP in domain/port; reads no table
    │       ├── fileimport/             # built: uploads, the import.process consumer (presentation/listener), PDFBox and POI readers behind domain/port/MarkReader, the review ("import" is a Java keyword)
    │       └── outbox/                 # OutboxFacade.add in the caller's transaction; a scheduler sends every second
    ├── main/resources/
    │   ├── application.yml · application-dev.yml   # application-prod.yml later
    │   ├── db/changelog/               # Liquibase: master file + v1/ numbered files; never edit applied files
    │   ├── persistence/                # MyBatis XML mappers, e.g. UserMyBatisRepository.xml
    │   ├── i18n/                       # mail.properties (English) and mail_az.properties: the email texts
    │   └── templates/mail/             # Thymeleaf email templates: {name}.html and {name}.txt
    └── test/java/app/ari/api/          # mirrors main: ArchitectureTest + @IntegrationTest classes (support/)
```

Request flow: `XControllerImpl` → `XFacade` → `XService` → `XRepository` (port) → adapter in
`infrastructure/`.

Rules:

- **Presentation:** the `XController` interface holds the request mappings and Swagger annotations
  (`@Tag`, `@Operation`); `impl/XControllerImpl` is the `@RestController` and only calls the facade.
- **Facade:** orchestrates one use case and maps DTOs with MapStruct. No business rules here.
- **Service:** business rules. It talks only to the repository ports of its own feature, and
  returns `Optional` or `boolean` for "not possible" (username taken, wrong password).
- **Infrastructure:** adapters that implement the ports. A MyBatis adapter is a `@Mapper` interface
  that extends the port, with its SQL in `resources/persistence/<Name>.xml`; rows map into the
  domain records by constructor argument name. Unique keys guard data: inserts use
  `ON CONFLICT DO NOTHING` and return `false`. Tables come from a Liquibase changeset in the same
  commit.
- **Exceptions:** the facade turns "not possible" into feature exceptions, which extend the base
  classes in `common/exception` (`NotFoundException`, `BadRequestException`,
  `UnauthorizedException`, `ConflictException`) and sit in folders by type. `GlobalExceptionHandler`
  turns them into RFC 9457 Problem Details with a stable `code` (`NOT_FOUND`, `VALIDATION_FAILED`,
  `USERNAME_TAKEN`).
- **Transactions:** `@Transactional` on facade use cases, so a use case saves all or nothing.
- **Between features:** call the other feature's facade; never use its domain or infrastructure.
  `ArchitectureTest` (ArchUnit) checks this and the layer directions: presentation → application →
  domain, and infrastructure → domain only. When asking a facade would make two facades depend on
  each other, a feature's SQL may read (never write) the other's table, and the other's enum values
  cross as text: the Sources page sums up `saves` and the notes quoting them in
  `SourceMyBatisRepository.xml`, because the save facade already calls the source facade (and the
  note facade calls the save facade).
- Records for DTOs, constructor injection, an injected `Clock`, UUID ids, all times in UTC.
- **CORS:** allow the frontend's origin with credentials (the sign-in cookies), read from
  `app.cors.allowed-origins` in `application.yml`, which takes `APP_CORS_ALLOWED_ORIGINS`
  (comma-separated; default `http://localhost:3000`). Extension origins (`moz-extension://*`,
  `chrome-extension://*`; Firefox gives each install its own uuid) are allowed by pattern,
  without credentials: the extension sends its Bearer token, never cookies.
- **Sign-in:** every endpoint except `/api/v1/auth/**`, health and Swagger needs a signed-in user;
  read their id from `@AuthenticationPrincipal Jwt` (`jwt.getSubject()`). The cookie and token
  rules are in `docs/architecture.md`.
- **Config and secrets:** secrets and values that differ per environment never go in the repo.
  `application.yml` refers to them as `${VAR:default}` placeholders (no default for real
  secrets). Locally they come from the git-ignored `.env`, which the `dev` profile imports
  (`optional:file:.env[.properties]` in `application-dev.yml`), so tests and CI never read it.
  Docker Compose passes it in with `env_file`. Real environment variables always win. The format is
  `KEY=value`, with no quotes and no `export`, so Spring and Compose read it the same way. List
  every key, with no value, in `.env.example`.

## 5. API contract

Base path `/api/v1`, JSON, times as ISO-8601 in UTC, null fields left out of responses. The
frontend repo mirrors this contract, so keep `/v3/api-docs` accurate: the frontend will generate
its client from it later.

```java
public record MeResponse(UUID id, String username, String fullName, LocalDate birthDate, String email, String pendingEmail, String photoUrl, Plan plan, int savesCount) {}
public record SaveResponse(
    UUID id, SaveKind kind, HighlightColor color,   // color: not for a CLIP
    ClipType clipType, String text, String contentMd, String imageUrl, // clips; imageUrl is signed for an hour
    String comment, String section, Integer pageNumber, // pageNumber: imported files only
    List<String> tags, SourceResponse source, Instant createdAt) {}
public record SourceResponse(UUID id, SourceKind kind, String name, String title, String author, String url) {}
public record FacetsResponse(int total, List<TypeCount> types, List<TagFacet> tags, List<ColorFacet> colors, List<SourceFacet> sources) {}
public record SourceSummaryResponse(
    UUID id, SourceKind kind, String name, String title, String author, String url, // as SourceResponse
    int savesCount, int highlightsCount, int clipsCount, int importsCount,
    List<String> colors, LatestHighlight latestHighlight, // colors: most used first; latestHighlight: left out when nothing has a color
    Instant firstSavedAt, Instant lastSavedAt) {}
public record LatestHighlight(String text, String color) {}
public record SourceCountsResponse(int total, List<KindCount> kinds) {} // KindCount(SourceKind kind, int count), both kinds
// SourceSummaryResponse also has List<QuotingNote> notes: QuotingNote(UUID id, String title, int quotesCount)
public record NoteSummaryResponse(UUID id, String title, List<UUID> saveIds, boolean empty, Instant createdAt, Instant updatedAt) {}
public record NoteResponse(UUID id, String title, List<NoteBlockResponse> blocks, Instant createdAt, Instant updatedAt) {}
public record NoteBlockResponse(NoteBlockKind kind, String text, SaveResponse save) {} // text: TEXT only; save: QUOTE only
public record ImportResponse(UUID id, String fileName, long sizeBytes, ImportStatus status, String errorCode, // errorCode: FAILED only
    int marksCount, int keptCount, UUID sourceId, String title, String author, // the file's source, once read
    Instant readAt, Instant reviewedAt, Instant createdAt, List<ImportMarkResponse> marks) {} // marks: GET /imports/{id} only
public record ImportMarkResponse(UUID id, MarkType type, MarkColor color, FileColor fileColor, String text,
    Integer pageNumber, String comment, String imageUrl, boolean selected, UUID saveId) {} // imageUrl: drawn and handwritten, signed for an hour
public record ImportsResponse(List<ImportResponse> items, UploadSpaceResponse space) {} // UploadSpaceResponse(long usedBytes, long limitBytes)
public record ReviewRequest(String title, String author, String tag, MarkColor color, List<UUID> selectedMarkIds) {}
public record ReviewResponse(int added, int removed, UUID sourceId) {}
public record PageResponse<T>(List<T> items, String nextCursor) {}

enum Plan { NONE, HIVE }
enum SaveKind { HIGHLIGHT, CLIP, IMPORT }
enum ClipType { TABLE, IMAGE }
enum HighlightColor { YELLOW, GREEN, BLUE, PINK, PURPLE }
enum SourceKind { WEBSITE, FILE }
enum SaveOrder { NEWEST, PAGE }
enum NoteBlockKind { TEXT, QUOTE }
enum ImportStatus { QUEUED, READING, READY, SAVED, EMPTY, FAILED, DISCARDED }
enum MarkType { HIGHLIGHT, UNDERLINE, DRAWN, HANDWRITTEN, STRIKE, NOTE }
enum MarkColor { YELLOW, GREEN, BLUE, PINK, PURPLE } // as HighlightColor
enum FileColor { ORANGE, RED } // a file's own color that had to be mapped
```

| Endpoint | Feature | Behavior |
|---|---|---|
| `POST /auth/register` `{ username, password, fullName, birthDate }` | `account` | 201 + `MeResponse` + both cookies; 409 `USERNAME_TAKEN` |
| `POST /auth/login` `{ username, password }` | `account` | 200 + `MeResponse` + both cookies; 401 `INVALID_CREDENTIALS` |
| `POST /auth/refresh` (`ari_refresh` cookie) | `account` | 204 + new cookies; 401 clears them |
| `POST /auth/logout` (`ari_refresh` cookie) | `account` | 204, ends the session and clears the cookies |
| `POST /me/extension-code` | `account` | 200 `{ code }`: the signed-in website's code for the extension (F13), 60 seconds, single use, its SHA-256 in Redis |
| `POST /auth/extension/code` `{ username, password }` | `account` | 200 `{ code }` for that account, no cookies: the website's extension sign-in page, so the extension can use another account than the website; 401 `INVALID_CREDENTIALS` |
| `POST /auth/extension/token` `{ code }` | `account` | 200 `{ accessToken, refreshToken }` as JSON, no cookies: the extension's own session (`client EXTENSION`); 401 `INVALID_CODE` |
| `POST /auth/extension/refresh` `{ refreshToken }` | `account` | 200 new pair, the cookie rotation rules; only `EXTENSION` tokens; 401 `UNAUTHORIZED`, clearing no cookies |
| `POST /auth/extension/logout` `{ refreshToken }` | `account` | 204, ends the extension's session; the website's stays |
| `GET /me` | `account` | The signed-in user (plan `NONE` until the trial starts, `savesCount` for the sidebar, `email` verified and `pendingEmail` waiting); 401 when signed out |
| `PUT /me` `{ username, fullName, birthDate }` | `account` | 200 + `MeResponse`; the sign-up rules; 409 `USERNAME_TAKEN` in any case |
| `PUT /me/photo` (multipart `photo`) | `account` | 200 + `MeResponse` with `photoUrl` (signed, an hour); JPEG, PNG or WebP by its bytes, at most 2 MB: 400 `INVALID_PHOTO`, 413 `FILE_TOO_LARGE`; the old photo is deleted |
| `DELETE /me/photo` | `account` | 204, deletes the photo |
| `PUT /me/email` `{ email }` | `account` | 202 + `MeResponse`, `pendingEmail` set and its verify link queued (200, nothing sent, when it already is the verified email); 429 `RATE_LIMITED` over 1 a minute or 5 a day |
| `POST /me/email/resend` | `account` | 202 + `MeResponse`; 400 when no email is pending; 429 `RATE_LIMITED` |
| `DELETE /me/email` | `account` | 204, clears the verified and the pending email |
| `POST /auth/password-reset` `{ login }` | `account` | Public. 204, a reset link (30 minutes, single use) queued to the verified email of the account with that username or verified email, in the `ari_lang` language; 404 `ACCOUNT_NOT_FOUND`; 409 `NO_EMAIL`, `EMAIL_NOT_CONFIRMED`; 429 `RATE_LIMITED` over 20 an hour per address or the account's 1 email a minute, 5 a day |
| `POST /auth/password-reset/check` `{ code }` | `account` | Public. 200 `{ username }`, without using the link up; 401 `INVALID_CODE`; 410 `CODE_EXPIRED`, `CODE_USED` |
| `POST /auth/password-reset/confirm` `{ code, password }` | `account` | Public. 204, the new password (sign-up rules), the link used up and every session of the account ended; the same code errors |
| `POST /auth/verify-email` `{ code }` | `account` | Public. 204, the pending email becomes the verified one; 401 `INVALID_CODE`; 409 `EMAIL_TAKEN` |
| `POST /saves` `{ kind, color, clipType, text, contentMd, comment, section, tags, source: { url, name, title, author } }` | `save` | 201 + `SaveResponse`. A `HIGHLIGHT` needs a `color`; a `CLIP` has no color and a `clipType`: a `TABLE` its `contentMd`. The source is found by its address (fragment dropped) or created. 400 `VALIDATION_FAILED`, `INVALID_SAVE`, `INVALID_URL`. No plan or count limit yet |
| `POST /saves` (multipart: `save` JSON part, `image` part) | `save` | An image clip (`kind CLIP`, `clipType IMAGE`): a JPEG, PNG or WebP of at most 2 MB, stored in MinIO first. 201 + `SaveResponse` with `imageUrl`; 400 `INVALID_IMAGE`, 413 `FILE_TOO_LARGE` |
| `GET /saves?type=&tag=&color=&source=&q=&order=&cursor=&limit=` | `save` | The signed-in user's saves, newest first, 40 per page (`limit` 1 to 100); `tag`, `color` and `source` repeat; any value within a group, every group given. `q` (F10): words that start words of the text, comment or section, or of the source's name, title or author (`SourceFacade.findIdsMatching`), or part of the text; accents and case don't count. `order=PAGE`: by page number (none counts as 0), then oldest first, for a source's page. `PageResponse<SaveResponse>`; 400 `INVALID_CURSOR`, also for a cursor of the other order |
| `GET /saves/facets?type=&tag=&color=&source=&q=` | `save` | Type tab counts (among the saves that match the filters) and the tag, color and source choices with their counts (whole Library) |
| `GET /saves/{id}` | `save` | One save with its source and tags (`SaveResponse`), for the website's reading view; 404 `NOT_FOUND`, also for someone else's |
| `PATCH /saves/{id}` `{ color, comment, tags }` | `save` | A field left out stays; `tags` replaces them all. 200 + `SaveResponse`; 404 `NOT_FOUND`; 400 `INVALID_SAVE` for a color on a clip |
| `DELETE /saves/{id}` | `save` | 204; its image is deleted after the commit; `users.saves_count` follows; 404 `NOT_FOUND` |
| `GET /sources?kind=&q=&cursor=&limit=` | `source` | The Sources page: the user's sources with saves, the one saved from most recently first, 40 per page (`limit` 1 to 100); `q` searches the name, title and author (F10). `PageResponse<SourceSummaryResponse>`; 400 `INVALID_CURSOR` |
| `GET /sources/counts` | `source` | `SourceCountsResponse`: sources with saves, in all and per kind (the kind tabs) |
| `GET /sources/{id}` | `source` | One source with what is saved from it (`SourceSummaryResponse`); its saves come from `GET /saves?source={id}&order=PAGE`. 404 `NOT_FOUND`, also for someone else's or one with no saves left |
| `GET /notes?q=` | `note` | `NoteSummaryResponse[]`: every note, the one changed most recently first, without its text; `q` searches the title and paragraphs (F10) |
| `POST /notes` `{ title, blocks: [{ kind, text, saveId }] }` | `note` | 201 + `NoteResponse`. A `TEXT` block has `text` (empty for a blank paragraph), a `QUOTE` block a `saveId`; quotes of saves that aren't the user's are left out. 400 `VALIDATION_FAILED` (title over 200, over 1000 blocks, a paragraph over 20000), `INVALID_NOTE` |
| `GET /notes/{id}` | `note` | `NoteResponse`, each quote with its save as it is now; 404 `NOT_FOUND`, also for someone else's |
| `PUT /notes/{id}` (as `POST`) | `note` | 200 + `NoteResponse`: the title and every block replace what was there, in one transaction; 404 `NOT_FOUND` |
| `DELETE /notes/{id}` | `note` | 204; the saves it quotes stay; 404 `NOT_FOUND` |
| `GET /imports` | `fileimport` | `ImportsResponse`: the files not dismissed, newest first (200 at most), and the upload space; the website polls it every 2 s while one is `QUEUED` or `READING` |
| `POST /imports` (multipart `file`) | `fileimport` | 202 + `ImportResponse` (`QUEUED`): a PDF or DOCX by its bytes, of any size that fits in the space left (everyone has 50 MB until billing; the servlet takes up to 50 MB), stored in MinIO and queued through the outbox; 400 `UNSUPPORTED_FILE`, `NO_SPACE_LEFT`; 413 `FILE_TOO_LARGE` over 50 MB |
| `GET /imports/{id}` | `fileimport` | `ImportResponse`, with its marks once `READY` or `SAVED`; 404 `NOT_FOUND` |
| `PUT /imports/{id}/review` `{ title, author, tag, color, selectedMarkIds }` | `fileimport` | 200 + `ReviewResponse`: ticked marks without a save become saves (`IMPORT`, or an image clip for a handwritten mark's picture) through `SaveFacade.createImported`; saved ones unticked are deleted; the source takes the title and author; `SAVED`. 409 `INVALID_IMPORT_STATE` |
| `POST /imports/{id}/retry` | `fileimport` | 202, a `FAILED` file back to `QUEUED`; 409 otherwise |
| `POST /imports/{id}/dismiss` | `fileimport` | 204, hides a `FAILED`, `EMPTY` or `DISCARDED` file; 409 otherwise |
| `DELETE /imports/{id}` | `fileimport` | 204, discards a `READY` file: its marks and source go; 409 otherwise |

Saves, tags, sources and notes are real PostgreSQL data (changesets 004 to 009); there is no sample data.

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
  `jdbc:postgresql://postgres:5432/ari`. Next to them run `redis` (`redis:8`, which has the Bloom filter built in; no
  volume, since Redis holds nothing that only lives there), `rabbitmq` (`rabbitmq:4-management`,
  user and password from `.env`), `mailpit` (`axllent/mailpit:v1`, catches every email sent
  locally, accepts any SMTP login) and `minio` (`pgsty/minio`, pinned: a community fork, because
  MinIO no longer publishes its own images; key and secret from `.env`, files in the `minio-data`
  volume). In compose, `api` reaches MinIO at `http://minio:9000` but signs photo links for
  `http://localhost:9000`, where the browser reaches it. For `./gradlew bootRun`, start everything
  but `api`: `docker compose up -d postgres redis rabbitmq mailpit minio`.

## 8. Rules for the AI assistant

- Build only what this file describes. No queues, storage, Redis or billing code until the matching
  TODO item starts.
- For every new aggregate, copy the layout of `feature/save` exactly, and the patterns of
  `feature/account` (MyBatis adapter, Liquibase changeset, exceptions, `@IntegrationTest`).
- Tests that need the database use `@IntegrationTest` (`src/test/java/app/ari/api/support`): one
  shared Spring context on a PostgreSQL container, so give every test its own data (for example a
  random username).
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
- The endpoints in section 5 work on real data: saves, their tags and sources survive a restart.
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
   frontend runs PostgreSQL with docker compose. Keep a `users.saves_count` counter for the
   Library count in the sidebar. Each library feature adds its own tables (see `docs/database.md`).
2. **Auth (done, F1):** Spring Security; our own JWT access tokens (15 minutes) plus rotating
   refresh tokens (httpOnly cookies for the website); username +
   password with bcrypt (email is optional and comes later, for password changes). Google sign-in (F9) is postponed. Rules in `docs/architecture.md`.
3. **Search (done, F10):** Postgres full-text search: a generated `tsvector` column with the
   `simple` config, an IMMUTABLE wrapper around `unaccent` (`ari_unaccent`), a GIN index, and
   `pg_trgm` for part of a word, in changeset 008 (saves and sources) and 009 (notes, over
   `notes.body`, the paragraphs joined). `common/search/SearchText` turns what is typed
   into a prefix `to_tsquery` text and a `LIKE` pattern. Results stay newest first (no ranking),
   so the cursor works as without a search.
4. **File imports (done, F5 and F11):** `common/client/ObjectStorage` talks to MinIO through the AWS SDK's S3 client
   (path-style, checksums only where required), so any S3-compatible server can replace it by
   config. MinIO's own images are no longer published and its community edition is archived, so
   compose and the tests use the pinned community fork `pgsty/minio`; watch it for security
   releases. Private bucket, created at startup when missing; signed links for images; keys in
   `docs/database.md` section 5. Profile photos use it already. Flow: upload to MinIO → outbox → RabbitMQ
   `import.process` → the import consumer in `ari-api` → PDFBox reads PDF highlight annotations
   with page numbers, POI reads DOCX highlights and comments → delete the file. Image OCR happens
   in the browser (frontend repo). EPUB is not supported.
5. **Async jobs and retries (done for email and `import.process`):** a transactional outbox
   in `feature/outbox`. Write the outbox row in the same transaction as the business change
   (`OutboxFacade.add` refuses to run without one); a scheduled publisher sends pending rows to
   RabbitMQ every second (`common/client/MessagePublisher`, publisher confirms) and marks them sent,
   and deletes sent rows after 7 days. `config/RabbitConfig` declares each work queue, bound to the
   `ari` direct exchange by its own name, with `.retry.2s`, `.retry.10s` and `.retry.60s` delay
   queues (message TTL, dead-lettered back to the work queue) and a `.dlq`. Consumers retry 2 s → 10 s → 60 s, then go to
   a dead-letter queue; they are idempotent by event id (the import consumer through `processed_events`, written with
   `OutboxFacade.markProcessed` in its own transaction); failed jobs are visible to the user. A
   message that can never succeed throws `UnprocessableMessageException` and skips the retries;
   `FailedMessageHandler.handle` says when it dead-lettered one, so the consumer can mark it failed.
   Queues: `import.process`, `email.transactional`, `email.bulk`. The mail sender is `feature/mail`
   inside `ari-api` for now: it consumes the `email.*` queues, owns the Thymeleaf templates and sends
   over SMTP (Mailpit locally). It reads no table and talks to the rest only through RabbitMQ, so it
   can move to its own app unchanged. Diagram in `docs/architecture.md` section 1.1.
6. **Redis 8:** a Bloom filter of taken usernames for the live username check (the database
   still decides; `BF.*` commands through Spring Data Redis's `RedisConnection.execute`), rate
   limits, one-time codes (the extension's sign-in code, email verify links) and idempotency keys
   only. Keys in `docs/database.md` section 5.
7. **Billing:** Paddle Billing webhooks. One plan, Hive: $5/month or $48/year, the same price
   everywhere, with a 14-day free trial that starts once the user adds their card in Paddle checkout.
   No free plan and no save limit; `users.plan` is `NONE` until the trial starts. Verify webhook
   signatures and ignore duplicate events.
8. **API contract for the frontend:** keep springdoc's OpenAPI complete (every endpoint, field and
   error code), since the frontend generates its typed client from `/v3/api-docs`.
9. **Native image (optional):** compile ari-api to a GraalVM native executable with the
   `org.graalvm.buildtools.native` plugin (`./gradlew nativeCompile`, images from
   `ghcr.io/graalvm/native-image-community:25`) for much faster startup and lower memory. Builds
   take minutes and need a lot of RAM, and MyBatis XML mappers need reflection hints, so decide
   after the database step.
10. **Hosting:** only after everything works locally; a deploy workflow joins `ci.yml` then.
