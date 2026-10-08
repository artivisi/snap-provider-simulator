# SNAP Provider Simulator

Local simulator of a bank's SNAP (Standar Nasional Open API Pembayaran)
virtual account interface, for developing and testing partner apps without a
bank sandbox. It follows **BRI's publicly documented SNAP VA behaviour**;
where BRI's public pages are silent or login-gated, it follows the public
ASPI SNAP standard. Every such decision is recorded as an assumption (A-id) in
[`docs/spec-index.json`](docs/spec-index.json).

> Simulates BRI's publicly documented SNAP VA behaviour; not affiliated with
> or endorsed by PT Bank Rakyat Indonesia. For development and testing only:
> never put real credentials or customer data in it.

## Capabilities

| Capability | Where |
|---|---|
| Self-service partner onboarding: sign-up, VA prefix (`partnerServiceId`), client id and secret, key pair generation or public key upload, partner endpoint with test connection, token TTL, diagnostic mode, onboarding checklist | `/portal` |
| B2B access token, SHA256withRSA over `X-CLIENT-KEY\|X-TIMESTAMP` | `POST /snap/v1.0/access-token/b2b` |
| Service-call checks: bearer token, mandatory headers, `X-TIMESTAMP` skew, HMAC-SHA512 signature, `X-EXTERNAL-ID` unique per Jakarta day | all `/snap/v1.0/transfer-va/*` |
| Diagnostic mode (per partner): a failed signature check returns the expected string-to-sign and body hashes plus a hint (body not minified, hex signature, query string in path, `Bearer ` prefix). Never the signature. Real banks don't do this | SNAP error body `additionalInfo.diagnostic` |
| Bank-hosted VA: create, update, inquiry, delete, inquiry status | `/snap/v1.0/transfer-va/...` |
| Bank-to-partner calls (BRIVA Online): the bank gets a token from your app (signed with the bank key), sends inquiry and payment; resend with the same `X-EXTERNAL-ID` | portal Payments page, `/portal/api/biller-payments` |
| Customer pays a bank-hosted VA; your app is notified with the payment call carrying `trxId` | portal VAs page, `/portal/api/va-payments` |
| Error injection: timeout after processing, slow response, HTTP 500/502/503, invalid signature, late or dropped notification | portal Error injection page |
| Daily statement CSV from the bank ledger; reconciliation scenario (dropped notification, amount mismatch, duplicate notification) | portal Payments page |
| Exchange log of every inbound and outbound call: headers, body, string-to-sign, response | portal Exchange log page |
| Operator admin: partners with checklist progress, disable/enable, reset, delete | `/admin` |

Not in 2026.10-RELEASE: QRIS, `update-status` (29), `report` (35).

## Run

```bash
mkdir -p keys
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out keys/bank-private.pem
chmod 644 keys/bank-private.pem
curl -O https://raw.githubusercontent.com/artivisi/snap-provider-simulator/main/compose.yml
docker compose up -d
```

Open http://localhost:9090/portal. Image: `ghcr.io/artivisi/snap-provider-simulator:2026.10`,
linux/amd64 and linux/arm64.

### Configuration

All variables are required; there are no defaults. Startup stops and names
every missing one.

| Variable | Meaning | Example |
|---|---|---|
| `SIMULATOR_DB_URL` | PostgreSQL JDBC URL | `jdbc:postgresql://postgres:5432/simulator` |
| `SIMULATOR_DB_USERNAME`, `SIMULATOR_DB_PASSWORD` | Database login | |
| `SIMULATOR_TIMESTAMP_SKEW` | Allowed `X-TIMESTAMP` difference | `5m` |
| `SIMULATOR_BANK_PRIVATE_KEY_PATH` | Bank RSA key, PKCS#8 PEM, ≥ 2048 bits; its public key is served at `/keys/bank-public.pem` | `/keys/bank-private.pem` |
| `SIMULATOR_OPERATOR_USERNAME`, `SIMULATOR_OPERATOR_PASSWORD` | `/admin` login | |
| `SIMULATOR_OUTBOUND_CONNECT_TIMEOUT`, `SIMULATOR_OUTBOUND_READ_TIMEOUT` | Bank-to-partner HTTP timeouts | `5s`, `15s` |

## Onboarding a partner app

1. **Sign up** at `/portal/signup`: email, password, access token lifetime
   and diagnostic mode. The simulator issues the `partnerServiceId` (VA
   prefix: 8 characters, left-padded with spaces), the client id
   (`X-CLIENT-KEY`, `X-PARTNER-ID`) and the client secret (HMAC key). The
   secret is shown once; regenerate it from the overview if lost.
2. **Key** (`/portal/key`): either generate a pair in the simulator (the
   private key `private.pem`, PKCS#8, downloads once and is not stored), or
   generate locally and upload the public key:
   ```bash
   openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out private.pem
   openssl pkey -in private.pem -pubout -out public.pem
   ```
   A PKCS#1 key (`BEGIN RSA PRIVATE KEY`) converts with
   `openssl pkcs8 -topk8 -nocrypt -in old.pem -out private.pem`.
3. **Token**: `POST /snap/v1.0/access-token/b2b` with headers `X-CLIENT-KEY`,
   `X-TIMESTAMP` (ISO-8601 with offset), `X-SIGNATURE` =
   Base64(SHA256withRSA(private key, `clientId|timestamp`)) and body
   `{"grantType":"client_credentials"}`.
4. **Service calls**: `Authorization: Bearer <token>`, `X-TIMESTAMP`,
   `X-PARTNER-ID`, `CHANNEL-ID` (5 digits), `X-EXTERNAL-ID` (1–36 digits,
   unique per day), `X-SIGNATURE` = Base64(HMAC-SHA512(client secret,
   `METHOD:path:token:lowercase(hex(sha256(minified body))):timestamp`)).
5. **Partner endpoint** (`/portal/endpoint`), for bank-to-partner calls: your
   base URL and the client id and secret your app issued to the bank. The
   bank calls `{base URL}/v1.0/access-token/b2b`,
   `{base URL}/v1.0/transfer-va/inquiry` and `/payment`, and signs the token
   request with the key at `/keys/bank-public.pem`. From a container, a
   partner app on the host is `http://host.docker.internal:<port>`.

The overview page shows the checklist: key registered, token obtained,
signed call, VA created, partner endpoint reachable, inquiry answered,
payment acknowledged.

Every portal page has a JSON counterpart under `/portal/api` (HTTP Basic with
the portal email and password), e.g. `POST /portal/api/biller-payments`
`{"virtualAccountNo","amount","channelId"}`,
`GET /portal/api/statements/{yyyy-MM-dd}.csv`, `GET /portal/api/exchanges?limit=50`.

## Development

Java 25, Spring Boot 4.1, PostgreSQL 18 (Flyway), Thymeleaf + htmx + plain
CSS, JDK crypto only. Tests use Testcontainers and Playwright.

```bash
./mvnw verify   # tests, spec traceability, coverage ≥ 70 %, SpotBugs
```

Engineering rules, gates and release process:
[`docs/engineering-standard.md`](docs/engineering-standard.md). Spec sources
and the facts-only index: [`CLAUDE.md`](CLAUDE.md) and
[`docs/spec-index.json`](docs/spec-index.json). Code and tests reference
index items with `@SpecRef`.

## Related projects

| Project | Role |
|---|---|
| [payment-gateway](https://github.com/artivisi/payment-gateway) | Self-hosted VA gateway; a consumer of this simulator |
| [payment-simulator](https://github.com/artivisi/payment-simulator) | Simulates the bank-internal layer (ISO 8583, switching, HSM) underneath SNAP |

## License

Apache License 2.0
