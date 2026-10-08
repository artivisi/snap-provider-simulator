# Engineering Standard

Follows the Artivisi product-line practices of
[Balaka](https://github.com/artivisi/balaka), scaled down for a test tool: no
production hardening, but the same build, test, traceability and release
discipline. Where this document and Balaka differ, this document wins.

## Gates (build fails on any)

| Gate | Rule | Where |
|---|---|---|
| Traceability | Every `@SpecRef` resolves to an id in `docs/spec-index.json`; every `"scope": "in"` item has ≥1 `@SpecRef` in `src/main` **and** ≥1 in `src/test` | `SpecTraceabilityTest` |
| Coverage | JaCoCo line coverage ≥ 70% and branch coverage ≥ 70%, bundle level, over all surefire tests | `jacoco:check` bound to `verify` |
| Static analysis | SpotBugs (+ FindSecBugs) effort Max, threshold Medium, zero issues; each exclusion in `spotbugs-exclude.xml` carries a justification comment | `spotbugs:check` bound to `verify` |
| Spec index fresh | `python3 tools/spec/gen_index.py --check` passes against the committed index (CI runs it when `docs/sources/` is available; locally before editing the index) | manual / local |
| Definition of done | A capability is done only when a Playwright functional test exercises it through the UI or the portal API | review |

## Traceability

- `@SpecRef("<item id>")` or `@SpecRef("<item id>#<request|response>.<field>")`,
  repeatable, on types and methods.
- Main code marks the implementation; tests mark the test that verifies the item.
- Capabilities that are ours (portal, onboarding checklist, diagnostic mode,
  error injection, statement export) are `sim.*` items in the index, so they
  are traced the same way as bank-spec items.
- `SpecTraceabilityTest` scans compiled classes (main and test) and fails with
  the list of unresolved refs and of in-scope items missing a main or test ref.
- Items not built yet are listed in the test's `PENDING` set. An entry that
  becomes traced fails the build until it is removed, so the list only shrinks;
  a release requires it to be empty.

## Source layout (package-by-layer, as Balaka)

`com.artivisi.snapsimulator.{config, controller, controller.api, controller.snap,
service, repository, entity, dto, enums, exception, security, snap, bank, util}`

- `controller` (web, Thymeleaf), `controller.api` (portal/admin JSON),
  `controller.snap` (SNAP endpoints); `snap` holds signature, minifier, PEM,
  response-code logic with no Spring dependency; `bank` holds one `BankProfile`
  per simulated bank (everything that differs between banks).
- Controllers call services; services call repositories. No entity is bound as
  `@ModelAttribute`: web forms are form DTOs, API bodies are records in `dto`.
- Services: class-level `@Transactional(readOnly = true)`, `@Transactional` on writes.
- No fallback or default values: `orElseThrow(...)` with a message naming the id.
- Entities extend `BaseEntity` (UUID id, `@Version`, `createdAt`/`updatedAt`).
  No soft delete: reset and delete are real deletes.
- Errors: `@RestControllerAdvice` for `controller.api` with
  `ErrorResponse(error, message, fieldErrors, timestamp)`; SNAP endpoints
  always answer in SNAP format (`responseCode`, `responseMessage`); web errors
  render error pages. `server.error.include-*` = never.
- Logging: Logback pattern with CR/LF stripped; user-controlled values pass
  through `LogSanitizer`. No audit log.

## Database

- PostgreSQL 18; Flyway owns the schema; `ddl-auto=validate`.
- Production migrations `db/migration/V001__<snake_case>.sql`, three-digit
  versions; never edit a released migration, add a new one.
- Integration-test data: `src/test/resources/db/test/integration/V900__...`
  (location added only in the `test` profile).
- Functional-test data: created through the portal/API in the test itself or
  by `@TestConfiguration` initializers; no SQL fixtures.

## UI

- Thymeleaf with a fragment layout (`layouts/main :: layout(...)`), htmx 2
  vendored in `static/js`, plain CSS in `static/css`. No Tailwind, no build step.
- htmx partials in `<feature>/fragments/*.html`, returned when `HX-Request: true`.
- Element ids and `data-testid` on everything a test touches.
- UI text in English, hardcoded (no i18n).

## Security (test-tool level)

Kept: Spring Security form login for `/portal` and `/admin`, BCrypt, CSRF on
UI forms (htmx sends the token from meta tags), session cookie `HttpOnly` and
`SameSite=Strict`, `frame-ancestors 'none'`, actuator exposes `health` only,
secrets only from env, private keys never stored or logged.

Not implemented (simulator, not production): login lockout, rate limiting,
field encryption, security audit log, remember-me, CSP nonces, HSTS, DAST.

## Tests

| Kind | Naming | Runs | Setup |
|---|---|---|---|
| Unit | `*Test` | surefire | plain JUnit 5 + AssertJ |
| Integration | `*IntegrationTest` | surefire | `@SpringBootTest` + `@Import(TestcontainersConfiguration)` (`@ServiceConnection` `postgres:18-alpine`), profile `test` |
| Functional | `*FunctionalTest` | surefire | Playwright (`PlaywrightTestBase`, page objects with id locators); portal JSON via Playwright `APIRequestContext`; SNAP calls via a test SNAP client built on the `snap` package |
| Image | `*ImageTest`, `@Tag("image")` | separate CI step after `docker build` (`-Dgroups=image`); excluded from the default run via `excludedGroups=image` | Testcontainers runs the built image + PostgreSQL |

- `@DisplayName` on tests; assert real values (no `isNotNull`/`count > 0`-only
  assertions, no skip guards); wait for state, never fixed sleeps.
- Testcontainers container limits via `ContainerResourceDefaults`
  (`CreateContainerCmdModifier`), `pull-test-images.sh` for the local
  socktainer runtime.
- Coverage counts surefire tests only (image tests run in another JVM).

## CI (`.github/workflows/`)

- `ci.yml` on push/PR to `main`: Gitleaks secret scan (blocking) → `./mvnw verify`
  (tests, traceability, coverage, SpotBugs) with Playwright Chromium installed;
  surefire report and JaCoCo summary; CodeQL (`security-extended`).
- `docker-publish.yml`: on `main`, `*-RELEASE` tags, weekly rebuild and manual
  dispatch. Build, run image tests, then buildx `linux/amd64,linux/arm64` push to
  GHCR and, once the `DOCKERHUB_USERNAME`/`DOCKERHUB_TOKEN` secrets exist,
  Docker Hub `artivisi/snap-provider-simulator`, with SBOM and provenance
  attestation. Image tests: `./mvnw -P image-test test -Dimage.name=<image>`. Tags via `docker/metadata-action` as Balaka
  (`2026.10`, `latest` on release, `main`, `main-<sha>`).
- `release.yml`: on `*-RELEASE` (or manual dispatch with a tag), GitHub Release with the jar, CycloneDX SBOM
  and `docs/releases/<TAG>.md` as the body.

## Container

Balaka's Dockerfile pattern: Maven build stage with dependency cache, layered
jar extract; runtime `azul/zulu-openjdk-alpine:25-jre` with `tini`, non-root
user, `TZ=Asia/Jakarta`, `JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseG1GC
-XX:+ExitOnOutOfMemoryError"`, `HEALTHCHECK` on `/actuator/health/liveness`.
`.dockerignore` excludes `target`, `docs`, `tools`, IDE files; keeps `.git`
(git-commit-id).

## Versioning, commits, releases

- CalVer `YYYY.MM[.PATCH]-RELEASE`; pom holds `YYYY.MM-SNAPSHOT` between releases.
  First release `2026.10-RELEASE`.
- Release: write `docs/releases/<TAG>.md` from `docs/releases/TEMPLATE.md`,
  commit `release: bump version to <TAG>`, annotated tag, push; then
  `chore: prepare for next development iteration`.
- Conventional Commits with scope (`feat(portal): ...`, `fix(snap): ...`,
  `test(...)`, `docs`, `ci`, `build`, `chore`); message explains why.
- Trunk-based on `main`; short-lived branches when a PR is used.

## Docs

- `docs/adr/NNN-title.md` (Status / Context / Decisions with Decision,
  Rationale, Trade-offs / Consequences / References) and `docs/adr/README.md` index.
- `docs/releases/TEMPLATE.md` and one file per release.
- `SECURITY.md` (vulnerability reporting).

## Deferred (not in the first release)

Semgrep custom rules, OWASP dependency-check, SonarCloud, Codecov upload,
migration tests (start once a second migration exists after release).
