# SNAP Provider Simulator

Local simulator of the bank side of SNAP (Standar Nasional Open API
Pembayaran) virtual accounts, for developing and testing partner apps
without a bank sandbox. It simulates two banks, each from its own public
documentation:

- **BRI**: bank-hosted VA (create/update/inquiry/delete/status) and BRIVA
  Online (biller-hosted inquiry and payment). Where BRI's public pages are
  silent or login-gated, the public ASPI SNAP standard applies.
- **BCA**: Virtual Account for Biller (biller-hosted inquiry and payment flag,
  inquiry status).

Every interpretation is recorded as an assumption (A-id) in
[`docs/spec-index.json`](docs/spec-index.json).

> Simulates the publicly documented SNAP VA behaviour of BRI and BCA; not
> affiliated with or endorsed by PT Bank Rakyat Indonesia or PT Bank Central
> Asia. For development and testing only: never put real credentials or
> customer data in it.

## Accounts and bank connections

One portal account is one partner app. The app adds a **bank connection** for
each bank it works with, as it would register with each bank separately. Each
connection has its own VA prefix (`partnerServiceId`), client id and secret,
registered public key, partner endpoint, settings, onboarding checklist and
data (VAs, payments, ledger, statement, exchange log, injection rules).

## Capabilities

| Capability | BRI | BCA |
|---|---|---|
| B2B access token, SHA256withRSA over `X-CLIENT-KEY\|X-TIMESTAMP` | `POST /snap/v1.0/access-token/b2b` | `POST /openapi/v1.0/access-token/b2b` |
| Service-call checks: bearer token, mandatory headers, `X-TIMESTAMP` skew, HMAC-SHA512, `X-EXTERNAL-ID` unique per Jakarta day | yes | yes; `X-PARTNER-ID` must be the company code |
| Diagnostic mode: a failed signature check returns the expected string-to-sign, body hashes and a hint (body not minified, hex signature, query string, `Bearer ` prefix); never the signature | yes | yes |
| Bank-hosted VA: create, update, inquiry, delete | `/snap/v1.0/transfer-va/{create,update,inquiry,delete}-va` | not in BCA's public docs |
| Inquiry status | `POST /snap/v1.0/transfer-va/status` | `POST /openapi/v2.0/transfer-va/status` |
| Bank-to-partner: token from your app (signed with the bank's key), inquiry, payment; resend with the same `X-EXTERNAL-ID` | `{base}/v1.0/transfer-va/{inquiry,payment}` | same paths; BCA apps usually put `/openapi` in the base URL; resend sets `flagAdvise` Y |
| Customer pays a bank-hosted VA; payment call carries `trxId` | portal VAs page | n/a |
| Error injection: timeout after processing, slow response, HTTP 500/502/503, invalid signature, late or dropped notification | yes | yes |
| Daily statement CSV; reconciliation scenario (dropped notification, amount mismatch, duplicate notification) | yes | yes |
| Exchange log of every inbound and outbound call | yes | yes |
| Operator admin: partners, connections, checklist progress, disable/enable, reset, delete | `/admin` | |

Main differences the BCA profile reproduces: 25-character `X-TIMESTAMP`, token
response with `responseCode` 2007300 and `tokenType` bearer, `CHANNEL-ID`
95231 with 4-digit `channelCode`s (6011 ATM, 6014 internet banking, ...),
30-digit request ids, customer numbers up to 18 digits, inquiry accepted only
with `inquiryStatus` 00, payment answers 2002500/2022500/4042518 judged by
`paymentFlagStatus`, bills from the inquiry echoed with `billReferenceNo`.

Not in this release: QRIS; BRI `update-status` (29) and `report` (35); open,
partial and multi-amount VAs; choosing a subset of BCA bills.

## Run

```bash
mkdir -p keys
for b in bri bca; do openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out keys/$b-private.pem; done
chmod 644 keys/*.pem
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
| `SIMULATOR_BANK_KEY_DIR` | Directory with `bri-private.pem` and `bca-private.pem` (PKCS#8, RSA ≥ 2048); public keys are served at `/keys/bri-public.pem` and `/keys/bca-public.pem` | `/keys` |
| `SIMULATOR_OPERATOR_USERNAME`, `SIMULATOR_OPERATOR_PASSWORD` | `/admin` login | |
| `SIMULATOR_OUTBOUND_CONNECT_TIMEOUT`, `SIMULATOR_OUTBOUND_READ_TIMEOUT` | Bank-to-partner HTTP timeouts | `5s`, `15s` |

## Onboarding a partner app

1. **Sign up** at `/portal/signup` (email, password) and log in.
2. **Add a bank connection** on the account page: bank, access token lifetime,
   diagnostic mode. The simulator issues the `partnerServiceId` (VA prefix:
   8 characters, left-padded with spaces; for BCA its digits are the company
   code), the client id (`X-CLIENT-KEY`) and the client secret (HMAC key). The
   secret is shown once; it can be regenerated.
3. **Key** (connection → Key): generate a pair in the simulator (the private
   key `private.pem`, PKCS#8, downloads once and is not stored), or generate
   locally and upload the public key:
   ```bash
   openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out private.pem
   openssl pkey -in private.pem -pubout -out public.pem
   ```
   A PKCS#1 key (`BEGIN RSA PRIVATE KEY`) converts with
   `openssl pkcs8 -topk8 -nocrypt -in old.pem -out private.pem`. The same key
   pair may be registered on several connections.
4. **Token**: `POST` the bank's token path with `X-CLIENT-KEY`, `X-TIMESTAMP`
   (ISO-8601 with offset), `X-SIGNATURE` = Base64(SHA256withRSA(private key,
   `clientId|timestamp`)) and body `{"grantType":"client_credentials"}`.
5. **Service calls**: `Authorization: Bearer <token>`, `X-TIMESTAMP`,
   `X-PARTNER-ID` (BRI: client id; BCA: company code), `CHANNEL-ID`,
   `X-EXTERNAL-ID` (1–36 digits, unique per day), `X-SIGNATURE` =
   Base64(HMAC-SHA512(client secret,
   `METHOD:path:token:lowercase(hex(sha256(minified body))):timestamp`)).
6. **Partner endpoint** (connection → Endpoint): your base URL and the client id
   and secret your app issued to the bank. The bank calls
   `{base URL}/v1.0/access-token/b2b`, `{base URL}/v1.0/transfer-va/inquiry` and
   `/payment`, signing the token request with its key from `/keys/`. From a
   container, an app on the host is `http://host.docker.internal:<port>`.

The connection overview shows the checklist: key registered, token obtained,
signed call, VA created (BRI only), partner endpoint reachable, inquiry
answered, payment acknowledged.

Every portal page has a JSON counterpart under `/portal/api` (HTTP Basic with
the portal email and password): `POST /portal/api/signup`,
`GET /portal/api/me`, `POST /portal/api/connections`
`{"bank","tokenTtlSeconds","diagnosticMode"}`, and per connection
`/portal/api/connections/{id}/...`, e.g. `POST .../biller-payments`
`{"virtualAccountNo","amount","channelId"}`, `GET .../statements/{yyyy-MM-dd}.csv`,
`GET .../exchanges?limit=50`.

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
