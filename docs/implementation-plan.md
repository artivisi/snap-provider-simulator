# Implementation Plan — v0.1

Target: image pullable by **12 Okt 2026** (first training run). Scope = Labs 1–7 in CLAUDE.md. QRIS,
auto-pay and the payment-gateway integration are out of scope for v0.1.

## 1. Sources and what may be reused

Bank profile: **BRI** (see CLAUDE.md). Paths, headers, field names, VA
number layout and response codes follow BRI's public docs. Where those docs
are silent, the SNAP 1.0.2 standard applies, and each such assumption is
written down in `docs/bri/`.

| Source | Use |
|---|---|
| BRI public docs (OAuth, BRIVA Transfer-to-VA, BRIVA Online) | Field-level specs, summarised in our own words into `docs/bri/` **before coding**. The portal is JS-rendered: a plain fetch returns an empty page, so capture it in a browser. |
| SNAP standard (public BI/ASPI pages) | Fallback where BRI docs are silent. Link to them; never commit registration-gated documents. |

Nothing else is a source: no non-public bank specs, no client code.

Signature code is written fresh from the public standard, using JDK crypto only:

- RSA `clientId|timestamp` sign and verify
- HMAC-SHA512 string-to-sign
- lowercase hex SHA-256 of the body
- X.509 public key PEM parsing
- **PKCS#8 private key PEM parsing**, which Lab 4 needs to sign outbound calls
- **A JSON minifier.** It works at the token level and strips whitespace outside strings. A Jackson `readTree` round-trip is not used, because it can re-render numbers and escapes and so change the hash.

## 2. Stack

- Java 25, Spring Boot **4.1.0** (same as payment-gateway), Maven wrapper
- Jackson 3 (`tools.jackson.*`) through Boot 4
- JDK crypto only (no BouncyCastle). The README tech-stack row needs correcting.
- Thymeleaf + vendored htmx for the admin UI, with plain CSS and no Tailwind build step
- spring-boot-starter-actuator for the compose healthcheck
- Tests: JUnit 5, AssertJ, RestAssured. Lab 4 outbound tests use a test-only
  "fake partner" controller in the same app context.
- Package: `com.artivisi.snapsimulator`
- README/UI disclaimer: simulates BRI's publicly documented SNAP VA behaviour; not affiliated with or endorsed by PT Bank Rakyat Indonesia

## 3. Configuration (fail-fast, no defaults)

`@Validated @ConfigurationProperties` records (the same pattern as
`payment-gateway/config/GatewaySecurityProperties`). `application.yml` only
maps env vars; there are no literal values for secrets or paths.

```yaml
simulator:
  diagnostic-mode: ${SIMULATOR_DIAGNOSTIC_MODE}            # true in training
  token-ttl: ${SIMULATOR_TOKEN_TTL}                        # e.g. 15m; short (2m) for Lab 3 re-auth
  timestamp-skew: ${SIMULATOR_TIMESTAMP_SKEW}              # e.g. 5m
  inbound:                                                 # partner -> simulator (Labs 1–3, 6)
    client-id: ${SIMULATOR_CLIENT_ID}
    client-secret: ${SIMULATOR_CLIENT_SECRET}
  outbound:                                                # simulator -> partner (Labs 4–7)
    base-url: ${SIMULATOR_PARTNER_BASE_URL}
    client-id: ${SIMULATOR_BANK_CLIENT_ID}                 # bank's identity at the partner app
    client-secret: ${SIMULATOR_BANK_CLIENT_SECRET}
    private-key-path: ${SIMULATOR_BANK_PRIVATE_KEY_PATH}
    partner-service-id: ${SIMULATOR_PARTNER_SERVICE_ID}
    channel-id: ${SIMULATOR_CHANNEL_ID}
    connect-timeout: ${SIMULATOR_OUTBOUND_CONNECT_TIMEOUT}
    read-timeout: ${SIMULATOR_OUTBOUND_READ_TIMEOUT}
```

Key files are loaded at startup. A missing or unparsable key stops startup
with a message that names the path. The bank public key is served at
`GET /admin/keys/bank-public.pem` so participants can configure Lab 4
verification.

## 4. Endpoints

### SNAP inbound (partner → simulator)

Final paths, methods and service codes come from `docs/bri/`. BRI uses the
`/snap/v1.0/...` prefix. The service codes below are standard SNAP values and
must be checked against BRI's docs.

| Path (BRI doc) | Lab | SNAP service code |
|---|---|---|
| `POST /snap/v1.0/access-token/b2b` | 1 | 73 |
| `POST /snap/v1.0/transfer-va/create-va` | 3 | 27 |
| `PUT /snap/v1.0/transfer-va/update-va` (method per BRI doc) | 3 | 28 |
| `POST /snap/v1.0/transfer-va/inquiry-va` | 3 | 30 |
| `DELETE /snap/v1.0/transfer-va/delete-va` (method per BRI doc) | 3 | 31 |
| status / report endpoint as BRI documents it | 6 | 26 / 35 |

The pipeline for every service call is implemented once as a filter or
interceptor, in this order:

1. `Authorization: Bearer` present, known, and not expired. Otherwise `401xx01`.
2. `X-TIMESTAMP` parses and is within the skew. Otherwise `400xx01`/`401xx00`.
3. `X-SIGNATURE` HMAC check over `METHOD:requestURI(+query):token:sha256hex(minify(body)):timestamp`. Otherwise `401xx00`.
4. `X-EXTERNAL-ID` is unique for (clientId, date, service). Otherwise `409xx00`.
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

The flow starts from the admin action "customer pays at ATM"
(`POST /admin/biller-payments`, body `{virtualAccountNo, amount}`). The
partner endpoint paths and payloads follow BRIVA Online.

1. Access token from the partner app, signed with the "BRI" private key. The token is cached until it expires.
2. Inquiry, signed with HMAC.
3. Payment.
4. Record a ledger credit (§6) and the full exchange log entry.

`POST /admin/biller-payments/{id}/resend` resends step 3 with the same
`X-EXTERNAL-ID` and the same body (Lab 5).

### Admin (JSON API + HTML pages at `/admin`)

- VAs (bank-hosted): list, and pay (`POST /admin/vas/{vaNo}/pay`)
- Biller payments: trigger, resend
- Exchange log: every inbound and outbound call with headers, body, string-to-sign, and response. This is the main teaching view.
- Error injection rules: add, list, clear
- Statement: `GET /admin/statements/{yyyy-MM-dd}.csv`
- Lab 7 scenario seeder: `POST /admin/scenarios/reconciliation`
- `DELETE /admin/state` resets all state

## 5. Error injection (Lab 6)

Each rule is `{target, type, params, remaining}`. A rule is consumed per
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

## 6. State and statement (Lab 7)

In-memory `ConcurrentHashMap`s hold tokens, external ids, VAs, biller
payments, ledger entries, the exchange log (a bounded ring buffer), and
injection rules.

The **ledger** is the bank's truth: one credit per real payment. The CSV is
a projection of the ledger for one day, sorted by time, with a header row:

```
tanggal,waktu,no_referensi,virtual_account,nama,keterangan,kredit
```

The reconciliation seeder produces, for today:

- normal payments, each notified once
- **dropped**: a ledger credit with no notification (`DROP_NOTIFICATION`)
- **amount mismatch**: the ledger credits X while the notification carried `paidAmount` Y
- **duplicate**: the same payment notified twice under different `X-EXTERNAL-ID`s, so an app that dedups only on the external id records it twice; the ledger has one row

## 7. Delivery

- `Dockerfile`: multi-stage, `maven:3.9-eclipse-temurin-25` → `eclipse-temurin:25-jre`, non-root, `EXPOSE 9090`. Copy the boot jar by its exact name; the payment-gateway glob also matches the plain jar.
- `.github/workflows/release.yml`: on tag `v*`, run `mvn verify`, then buildx **linux/amd64 + linux/arm64**, then push `<registry>/snap-provider-simulator:{version}`. Most users run amd64, and the maintainer's local Docker has no working buildx, so CI builds the image.
- `compose.yml` example, with the bank key mounted from `./keys`.
- README updates: the Lab 4 flow, the diagnostic mode, the statement CSV, and the config table.

## 8. Schedule

| Day | Work |
|---|---|
| 6–7 Okt | Capture BRI docs into `docs/bri/`; skeleton, config, signature lib + tests (minifier, PKCS#8), token endpoint, diagnostic mode, key upload |
| 8 Okt | VA create/update/inquiry/delete/status, inbound pipeline, external-id and skew checks |
| 9 Okt | Outbound Lab 4 flow, resend, exchange log, admin UI |
| 10 Okt | Error injection, ledger, CSV, reconciliation seeder |
| 11 Okt | Dockerfile, release workflow, tag `v0.1.0`, pull + run on a clean amd64 machine |
| 12 Okt | Buffer; training setup guide pins the tag |

## 9. Decisions (2026-10-06)

1. **Registry: Docker Hub**, `artivisi/snap-provider-simulator`. This needs the repo secrets `DOCKERHUB_USERNAME` and `DOCKERHUB_TOKEN`, which Endy adds.
2. **Participant public key: uploaded through the admin UI** (`POST /admin/client-key`, PEM). The simulator starts without one. Until a key is uploaded, token calls fail with `401xx00` and the message "no client public key registered; upload at /admin". This is an unconfigured state, not a default value. `simulator.inbound.client-public-key-path` is dropped.
3. **Lab 7 anomalies**: as described in §6.
