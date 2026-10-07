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
- Spring Security: form login for two areas, partner portal and operator admin; BCrypt; CSRF on UI forms; SNAP endpoints stay token/signature-authenticated. Test-tool level only (see the standard)
- spring-boot-starter-actuator for the compose healthcheck
- Practices, gates (traceability, ≥70% line/branch coverage, SpotBugs zero),
  test layers, CI, container and release: `docs/engineering-standard.md`
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
  operator:
    username: ${SIMULATOR_OPERATOR_USERNAME}                   # /admin login
    password: ${SIMULATOR_OPERATOR_PASSWORD}
  outbound:
    connect-timeout: ${SIMULATOR_OUTBOUND_CONNECT_TIMEOUT}
    read-timeout: ${SIMULATOR_OUTBOUND_READ_TIMEOUT}
```

The bank key is loaded at startup; a missing or unparsable key stops startup
with a message that names the path. The bank public key is served at
`GET /keys/bank-public.pem` (public) so partner apps can verify bank-to-partner
calls.

### Partner onboarding (self-service portal, `/portal`)

Several partner apps share one simulator; every row of state belongs to one
partner. Partners onboard themselves, following the technical steps of a bank
developer portal:

1. **Sign up** (open): email + password, then log in. One account = one partner app.
2. **Application issued**: the simulator assigns a unique `partnerServiceId`
   (VA prefix) and generates `clientId` and `clientSecret`. The secret is shown
   once; it can be regenerated, which invalidates the old one.
3. **SNAP key**: the key page offers two ways; either one replaces the
   current key. The bank public key is downloadable on the same page.
   - **Generate in the simulator**: RSA-2048 key pair; the public key is stored,
     and the private key (PKCS#8 PEM) is offered as a one-time download and
     never stored. Leaving the page without downloading means generating again.
   - **Generate locally and upload**: the page shows the commands, then accepts
     the public key PEM (file or paste):
     ```
     openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out private.pem
     openssl pkey -in private.pem -pubout -out public.pem
     ```
     `genpkey` writes PKCS#8 (`BEGIN PRIVATE KEY`), which JCA's
     `PKCS8EncodedKeySpec` reads directly; the page states this, and notes
     that `openssl genrsa` on OpenSSL 1.1 writes PKCS#1 (`BEGIN RSA PRIVATE KEY`)
     and shows `openssl pkcs8 -topk8 -nocrypt -in old.pem -out private.pem`
     to convert.
   - Upload validation: rejected unless it parses as an X.509
     `SubjectPublicKeyInfo` RSA key ≥ 2048 bits. A pasted private key is
     rejected with a message saying so, and is not stored or logged.
4. **Partner endpoint** (for bank-to-partner calls): base URL plus the client id
   and secret the partner issued to the bank, all or none. "Test connection"
   makes the bank request a token from the partner and shows the exchange.
5. **Settings**: `tokenTtl` and `diagnosticMode`, both required at sign-up
   (no defaults); editable.

**Onboarding checklist**, each item stamped with the time it first succeeded:

| Item | Passes when |
|---|---|
| Key registered | public key uploaded |
| Token obtained | first successful `access-token/b2b` |
| Signed call | first service call passing the signature check |
| VA created | first successful `create-va` |
| Partner endpoint reachable | bank obtained a token from the partner |
| Inquiry answered | first bank-to-partner inquiry with a 2xx `responseCode` |
| Payment acknowledged | first bank-to-partner payment with `paymentFlagStatus` `00` |

Partner workspace (logged-in partner sees only its own data): credentials,
key, endpoint, settings, checklist, VAs, simulate customer payment, exchange
log, error injection, statement CSV, reset data.

### Operator admin (`/admin`)

Login from env (`simulator.operator.*`). Lists partners with checklist
progress; can disable/enable a partner (disabled partners get `401xx00` on every
call), reset a partner's data, and delete a partner.
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

The flow starts from the portal action "simulate customer payment"
(`POST /portal/api/biller-payments`, body `{virtualAccountNo, amount, channelId}`). The
partner endpoint paths and payloads follow BRIVA Online.

1. Access token from the partner app, signed with the "BRI" private key. The token is cached until it expires.
2. Inquiry, signed with HMAC.
3. Payment.
4. Record a ledger credit (§6) and the full exchange log entry.

`POST /portal/api/biller-payments/{id}/resend` resends step 3 with the same
`X-EXTERNAL-ID` and the same body (idempotency test).

### Portal and admin routes

Each portal page has a JSON counterpart under `/portal/api/...` with the same
session auth, so tests and scripts can drive it.

- Portal: sign-up, login, credentials (regenerate secret), key upload, bank key
  download (`GET /portal/keys/bank-public.pem`, also public at
  `GET /keys/bank-public.pem`), endpoint + test connection, settings,
  checklist
- Portal workspace: VAs and pay action, simulate customer payment
  (`virtualAccountNo`, `amount`, `channelId`), resend, exchange log,
  error-injection rules, statement `.../statements/{yyyy-MM-dd}.csv`,
  reconciliation seeder, reset data
- Admin: partner list, disable/enable, reset, delete

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
| `partner` | account (email, password hash, enabled), credentials, key, endpoint, settings, checklist timestamps (§3) |
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

Per `docs/engineering-standard.md` (Container, CI, Versioning):

- Balaka-style layered Dockerfile on `azul/zulu-openjdk-alpine:25-jre`, port 9090.
- `ci.yml`, `docker-publish.yml` (amd64 + arm64, Docker Hub + GHCR, SBOM,
  provenance, image tests before push), `release.yml`.
- `compose.yml` example: simulator + `postgres:18-alpine` with a named volume and
  `pg_isready` healthcheck; bank key mounted from `./keys`.
- README: capabilities, onboarding procedure, config table, BRI disclaimer.
- First release `2026.10-RELEASE`; consumers pin image tag `2026.10`.

## 8. Schedule

| Day | Work |
|---|---|
| 6 Okt | Spec capture and index (done) |
| 7 Okt | Skeleton (pom gates: JaCoCo 70%, SpotBugs), config, Flyway schema, Testcontainers + Playwright bases, `@SpecRef` + traceability test, signature lib + tests, `ci.yml` |
| 8 Okt | Security (portal + admin login), sign-up, credentials, key upload, token endpoint, diagnostic mode, inbound check chain |
| 9 Okt | VA create/update/inquiry/delete/status, bank-to-partner flow + test connection, resend, checklist |
| 10 Okt | Exchange log, error injection, ledger, CSV, reconciliation seeder, portal + admin UI pages |
| 11 Okt | Dockerfile, image tests, publish + release workflows, release notes, tag `2026.10-RELEASE`, pull + run on a clean amd64 machine |
| 12 Okt | Setup verification only; no buffer left |

## 9. Decisions (2026-10-06)

1. **Registry: Docker Hub** `artivisi/snap-provider-simulator` (needs repo secrets `DOCKERHUB_USERNAME`, `DOCKERHUB_TOKEN`) and GHCR (`GITHUB_TOKEN`).
2. **Partner public key: uploaded by the partner in the portal** (PEM), replaceable. (Supersedes the 2026-10-06 single-key upload decision.)
3. **Reconciliation anomalies**: as described in §6.
4. **PostgreSQL, multi-partner** (2026-10-07): state persists across restarts and several partner apps share one simulator, each with its own credentials, keys, settings and data.
5. **Self-service onboarding** (2026-10-07): open sign-up; simulator assigns `partnerServiceId`; operator admin login from env. Supersedes admin-only registration. Included in v0.1.
6. **Engineering standard** (2026-10-07): Balaka practices scaled to a test tool; CalVer; English UI; no ZAP or production hardening; gates are spec↔code↔test traceability and ≥70% coverage. See `docs/engineering-standard.md`.
