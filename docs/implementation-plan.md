# Implementation Plan — v0.1

Target: image pullable by **12 Okt 2026**. Scope = the capabilities listed in CLAUDE.md. QRIS,
auto-pay and the payment-gateway integration are out of scope for v0.1.

## 1. Sources and what may be reused

Bank profile: **BRI** (see CLAUDE.md). Paths, headers, field names, VA
number layout and response codes follow BRI's public docs. Where those docs
are silent, the SNAP 1.0.2 standard applies, and each such assumption is
written down in local notes (`docs/bri/`, gitignored: spec content is not committed).

| Source | Use |
|---|---|
| BRI public docs (OAuth, BRIVA Online, Transfer-to-VA, Bank Statement) | Field-level specs. Linked from CLAUDE.md; captured locally with Playwright (JS-rendered portal), never committed. |
| SNAP standard (public BI/ASPI pages) | Fallback where BRI docs are silent. **Bank-hosted VA ops (create/update/inquiry/delete VA) and inquiry status come from the public ASPI Virtual Account standard**, because BRI's pages for them are login-gated; BRI's public conventions (path prefix, headers, VA layout, response code style) are applied on top. Link to them; never commit registration-gated documents. |

Nothing else is a source: no non-public bank specs, no client code.

Signature code is written fresh from the public standard, using JDK crypto only:

- RSA `clientId|timestamp` sign and verify
- HMAC-SHA512 string-to-sign
- lowercase hex SHA-256 of the body
- X.509 public key PEM parsing
- **PKCS#8 private key PEM parsing**, needed to sign outbound bank-to-partner calls
- **A JSON minifier.** It works at the token level and strips whitespace outside strings. A Jackson `readTree` round-trip is not used, because it can re-render numbers and escapes and so change the hash.

## 2. Stack

- Java 25, Spring Boot **4.1.0** (same as payment-gateway), Maven wrapper
- Jackson 3 (`tools.jackson.*`) through Boot 4
- JDK crypto only (no BouncyCastle). The README tech-stack row needs correcting.
- Thymeleaf + vendored htmx for the admin UI, with plain CSS and no Tailwind build step
- PostgreSQL 18; Spring Data JPA; Flyway owns the schema (`spring-boot-flyway`,
  `flyway-core`, `flyway-database-postgresql`); `ddl-auto: none`, `open-in-view: false`
- Spring `RestClient` for bank-to-partner calls
- spring-boot-starter-actuator for the compose healthcheck
- Tests:
  - Unit: JUnit 5, AssertJ (signatures with check values, minifier, PEM, VA layout)
  - Integration (`*IntegrationTest`): `@SpringBootTest` on a random port against a
    singleton Testcontainers `PostgreSQLContainer("postgres:18")`; RestAssured;
    a test-only fake partner controller in the same context for bank-to-partner calls
  - Functional (`*FunctionalTest`, Failsafe): Testcontainers runs the built image
    plus PostgreSQL on a shared network; covers fail-fast startup, migrations,
    healthcheck, key mount, a token + create-va smoke test, and a bank-to-partner
    call to a partner endpoint on the host. Fails, not skips, if the image is absent
- Package: `com.artivisi.snapsimulator`
- README/UI disclaimer: simulates BRI's publicly documented SNAP VA behaviour; not affiliated with or endorsed by PT Bank Rakyat Indonesia

## 3. Configuration and partner registry

### Environment (fail-fast, no defaults)

`@Validated @ConfigurationProperties` records (the same pattern as
`payment-gateway/config/GatewaySecurityProperties`). `application.yml` only
maps env vars; there are no literal values for secrets or paths. Only
deployment-wide settings live here:

```yaml
spring:
  datasource:
    url: ${SIMULATOR_DB_URL}
    username: ${SIMULATOR_DB_USERNAME}
    password: ${SIMULATOR_DB_PASSWORD}
simulator:
  timestamp-skew: ${SIMULATOR_TIMESTAMP_SKEW}                  # e.g. 5m
  bank:
    private-key-path: ${SIMULATOR_BANK_PRIVATE_KEY_PATH}       # one bank identity for all partners
  outbound:
    connect-timeout: ${SIMULATOR_OUTBOUND_CONNECT_TIMEOUT}
    read-timeout: ${SIMULATOR_OUTBOUND_READ_TIMEOUT}
```

The bank key is loaded at startup; a missing or unparsable key stops startup
with a message that names the path. The bank public key is served at
`GET /admin/keys/bank-public.pem` so partner apps can verify bank-to-partner
calls.

### Partners (registered at runtime, stored in PostgreSQL)

Several partner apps share one simulator; every row of state belongs to one
partner. A partner is registered through the admin UI/API
(`POST /admin/partners`):

| Field | Required | Meaning |
|---|---|---|
| `name` | yes | display name |
| `publicKeyPem` | yes | verifies the partner's token-request signature; replaceable later |
| `partnerServiceId` | yes | VA prefix, unique across partners (routes a VA number to its partner) |
| `tokenTtl` | yes | per partner, so one partner can exercise re-auth with a short TTL |
| `diagnosticMode` | yes | per partner |
| `outbound.baseUrl`, `outbound.bankClientId`, `outbound.bankClientSecret` | all or none | the partner's endpoint and the credentials the partner issued to the bank; without them, bank-to-partner actions for this partner fail with an explicit error |

The simulator generates `clientId` and `clientSecret` on registration and shows
them once in full, as a bank would issue them.
## 4. Endpoints

### SNAP inbound (partner → simulator)

Final paths, methods and service codes come from the linked sources. BRI uses the
`/snap/v1.0/...` prefix. Verified 2026-10-06: 73 against BRI OAuth; 26–31
against the ASPI Virtual Account standard (BRI's own pages are gated).

| Path | SNAP service code | Source |
|---|---|---|
| `POST /snap/v1.0/access-token/b2b` | 73 | BRI |
| `POST /snap/v1.0/transfer-va/create-va` | 27 | ASPI |
| `PUT /snap/v1.0/transfer-va/update-va` | 28 | ASPI |
| `POST /snap/v1.0/transfer-va/inquiry-va` | 30 | ASPI |
| `DELETE /snap/v1.0/transfer-va/delete-va` (JSON body) | 31 | ASPI |
| `POST /snap/v1.0/transfer-va/status` | 26 | ASPI |

Not implemented in v0.1: `PUT .../transfer-va/update-status` (29);
`.../transfer-va/report` (35, ASPI overview says GET, its sample uses POST).

The pipeline for every service call is implemented once as a filter or
interceptor, in this order:

1. `Authorization: Bearer` present, known, and not expired. Otherwise `401xx01`.
2. `X-TIMESTAMP` parses and is within the skew. Otherwise `400xx01`/`401xx00`.
3. `X-SIGNATURE` HMAC-SHA512 (Base64) check over `METHOD:path:token:sha256hex(minify(body)):timestamp`; path without query string (`docs/bri` A1, A3). Otherwise `401xx00`.
4. `X-EXTERNAL-ID` is unique for (clientId, Asia/Jakarta date), across services (`docs/bri` A9). Otherwise `409xx00`.
5. Error injection hook (§5).
6. Handler.

**Diagnostic mode.** When `diagnostic-mode=true` and step 1 (token) or step
3 fails, the error body adds an `additionalInfo.diagnostic` object:

```json
{ "expectedStringToSign": "...", "receivedTimestamp": "...",
  "minifiedBody": "...", "bodySha256": "...", "rawBodySha256": "...",
  "hint": "raw body hash matches but minified does not -> client did not minify" }
```

The response never contains the expected signature. The diagnostic exposes
the inputs to the signature, not its output.

### SNAP outbound (simulator → partner, biller-hosted VA)

The flow starts from the admin action "simulate customer payment"
(`POST /admin/biller-payments`, body `{virtualAccountNo, amount}`). The
partner endpoint paths and payloads follow BRIVA Online.

1. Access token from the partner app, signed with the "BRI" private key. The token is cached until it expires.
2. Inquiry, signed with HMAC.
3. Payment.
4. Record a ledger credit (§6) and the full exchange log entry.

`POST /admin/biller-payments/{id}/resend` resends step 3 with the same
`X-EXTERNAL-ID` and the same body (idempotency test).

### Admin (JSON API + HTML pages at `/admin`)

- Partners: register, list, edit settings, replace public key, regenerate secret
- Everything below is scoped to one partner (`/admin/partners/{id}/...`)
- VAs (bank-hosted): list, and pay (`POST /admin/partners/{id}/vas/{vaNo}/pay`)
- Biller payments: trigger, resend
- Exchange log: every inbound and outbound call with headers, body, string-to-sign, and response.
- Error injection rules: add, list, clear
- Statement: `GET /admin/statements/{yyyy-MM-dd}.csv`
- Reconciliation scenario seeder: `POST /admin/scenarios/reconciliation`
- `DELETE /admin/partners/{id}/state` deletes the partner's data, keeps its registration

## 5. Error injection

Each rule is `{partner, target, type, params, remaining}`. A rule is consumed per
matching call, with `remaining` defaulting to 1, which is set explicitly in
the request.

| Type | Target | Effect |
|---|---|---|
| `TIMEOUT_AFTER_PROCESSING` | inbound service | commit state, then sleep past the client timeout, then respond |
| `SLOW_RESPONSE` | inbound service | sleep N ms, then respond normally |
| `HTTP_ERROR` | inbound service | respond with 500/502/503 without processing |
| `INVALID_SIGNATURE` | outbound to partner | corrupt `X-SIGNATURE` on the call |
| `LATE_NOTIFICATION` | outbound payment | delay step 3 by N ms |
| `DROP_NOTIFICATION` | outbound payment | skip step 3; the ledger still credits |

## 6. State and statement

PostgreSQL tables, each keyed by partner:

| Table | Holds |
|---|---|
| `partner` | registration (§3) |
| `access_token` | tokens issued to partners, with expiry |
| `external_id` | (partner, Jakarta day, X-EXTERNAL-ID) unique |
| `virtual_account` | bank-hosted VAs and their state |
| `payment` | payments: bank-hosted pay actions and bank-to-partner notifications, with notification outcome |
| `ledger_entry` | bank credits |
| `exchange_log` | every inbound/outbound call: headers, body, string-to-sign, response, timing |
| `injection_rule` | pending error-injection rules |

Tokens the bank obtains from partners stay in memory: they are a cache and
are re-requested after a restart. Exchange log rows are deleted only by the
partner reset; no automatic retention in v0.1.

The **ledger** is the bank's truth: one credit per real payment. The CSV is
a projection of the ledger for one day, sorted by time. Columns and their mapping are in `docs/bri/statement.md`:

```
transaction_date,transaction_id,type,amount,currency,virtual_account_no,remark
```

The reconciliation seeder (per partner) produces, for today:

- normal payments, each notified once
- **dropped**: a ledger credit with no notification (`DROP_NOTIFICATION`)
- **amount mismatch**: the ledger credits X while the notification carried `paidAmount` Y
- **duplicate**: the same payment notified twice under different `X-EXTERNAL-ID`s, so an app that dedups only on the external id records it twice; the ledger has one row

## 7. Delivery

- `Dockerfile`: multi-stage, `maven:3.9-eclipse-temurin-25` → `eclipse-temurin:25-jre`, non-root, `EXPOSE 9090`. Copy the boot jar by its exact name; the payment-gateway glob also matches the plain jar.
- `.github/workflows/release.yml`: on tag `v*`, run `mvn verify` (unit + integration), build the image, run the functional tests against it, then buildx **linux/amd64 + linux/arm64**, then push `<registry>/snap-provider-simulator:{version}`. Most users run amd64, and the maintainer's local Docker has no working buildx, so CI builds the image.
- `compose.yml` example: simulator + `postgres:18` with a named volume and `pg_isready` healthcheck; bank key mounted from `./keys`.
- README updates: the bank-to-partner flow, the diagnostic mode, the statement CSV, and the config table.

## 8. Schedule

| Day | Work |
|---|---|
| 6 Okt | Spec capture and index (done) |
| 7 Okt | Skeleton, config, Flyway schema, Testcontainers base test, `@SpecRef` + traceability test, signature lib + tests |
| 8 Okt | Partner registry + admin API, token endpoint, diagnostic mode, inbound check chain, VA create/update/inquiry/delete/status |
| 9 Okt | Bank-to-partner flow, resend, exchange log, admin UI |
| 10 Okt | Error injection, ledger, CSV, reconciliation seeder |
| 11 Okt | Dockerfile, functional tests, release workflow, tag `v0.1.0`, pull + run on a clean amd64 machine |
| 12 Okt | Buffer |

## 9. Decisions (2026-10-06)

1. **Registry: Docker Hub**, `artivisi/snap-provider-simulator`. This needs the repo secrets `DOCKERHUB_USERNAME` and `DOCKERHUB_TOKEN`, which Endy adds.
2. **Partner public key: provided at partner registration** through the admin UI/API (PEM), replaceable later. (Supersedes the 2026-10-06 single-key upload decision.)
3. **Reconciliation anomalies**: as described in §6.
4. **PostgreSQL, multi-partner** (2026-10-07): state persists across restarts and several partner apps share one simulator, each with its own credentials, keys, settings and data.
