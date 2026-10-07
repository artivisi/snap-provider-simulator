# CLAUDE.md — SNAP Provider Simulator

Local simulator of BRI's SNAP (Standar Nasional Open API Pembayaran) VA side,
for developing and testing partner apps against SNAP without a bank sandbox.
Public repo, Apache 2.0. README.md holds the original design (written for the
gateway use case). Status: design only, no code yet (as of 2026-10-06).
Plan: `docs/implementation-plan.md`.

The simulator is a general-purpose tool: no reference to any training, course,
lab, participant, client or engagement in code, docs, config or UI. Internal
context (why features exist, deadlines, private source locations) lives in
`CLAUDE.local.md`, which is gitignored. Nothing from it goes into tracked files.

## Consumers

1. Developers of partner apps (VA billers, payment gateways) testing SNAP
   integration locally. Distributed as a Docker image; consumers pin a tag.
2. [payment-gateway](https://github.com/artivisi/payment-gateway): the
   self-hosted VA gateway. Same stack (Java 25, Spring Boot 4.1).

QRIS endpoints in README are out of scope for v0.1.

## Bank profile: BRI

The simulator emulates **BRI's publicly documented SNAP VA behaviour**,
covering both VA models (bank-hosted and biller-hosted).

Spec content is not ours and is never committed. `docs/sources/` (raw
Playwright captures; the portals are JS-rendered) and `docs/bri/` (working
notes) are local and gitignored. Tracked files link to the original pages
below; `docs/spec-index.json` holds the facts-only index (see Spec
traceability).

- OAuth: https://developers.bri.co.id/en/snap-bi/apidocs-oauth-snap-bi
- Bank-hosted VA (create/update/inquiry/delete-va, status): BRI's page
  (BRIVA WS) is login-gated and NOT used. Follow the public ASPI standard:
  https://apidevportal.aspi-indonesia.or.id/api-services/transfer-kredit/virtual-account
  with BRI's public conventions (`/snap/v1.0` prefix, headers, codes).
- Security standard: https://apidevportal.aspi-indonesia.or.id/api-services/keamanan
- BRIVA Online, biller-hosted (BRI → partner inquiry/payment), v2:
  https://developers.bri.co.id/en/snap-bi/apidocs-virtual-account-briva-online-snap-bi
- BRI SNAP bank statement (reference for the statement export):
  https://developers.bri.co.id/en/snap-bi/api-bank-statement-snap-bi

Match BRI's paths, headers, field names, VA number layout and response codes
as documented. Where BRI is silent or gated, follow the public ASPI standard
and record the decision as an assumption (A-id) in `docs/spec-index.json`.
Never use login-gated pages, even if an account becomes available.

## Capabilities (v0.1)

| Capability | Spec |
|---|---|
| **Partner onboarding portal**: open sign-up; simulator assigns VA prefix and issues client id/secret; partner generates a key pair in the simulator (private key one-time download) or uploads a locally generated public key (openssl commands shown), registers its endpoint (with test connection), sets token TTL and diagnostic switch; onboarding checklist; all state scoped per partner | ours |
| Operator admin: list partners and checklist progress, disable, reset, delete | ours |
| B2B access token, SHA256withRSA over `clientId\|timestamp` | BRI OAuth |
| HMAC-SHA512 signature verification on service calls | BRI OAuth, ASPI security |
| **Diagnostic mode** (per-partner switch): failed signature checks return the expected string-to-sign and body hashes, never the signature. Real banks don't do this | ours |
| Bank-hosted VA: create / update / inquiry / delete, inquiry status; configurable token TTL | ASPI VA |
| **Bank-to-partner calls**: simulator obtains a token from the partner app (signed with the simulator's bank key), then calls the partner's inquiry and payment endpoints; triggered from admin UI/API | BRIVA Online |
| Resend a payment with the same `X-EXTERNAL-ID` | BRIVA Online |
| Failure injection: timeout after processing, slow response, HTTP error, invalid signature, late or dropped notification | ours |
| Daily statement CSV from the bank ledger; reconciliation scenario seeder (dropped notification, amount mismatch, duplicate) | ours |
| Inbound `X-TIMESTAMP` skew and per-day `X-EXTERNAL-ID` uniqueness checks | ASPI security |
| Exchange log of every inbound/outbound call (headers, body, string-to-sign) | ours |

## Public repo — what may and may not go in

Allowed: our own implementation of BRI's public interface and the public SNAP
standard; generic SNAP signature code. NOT allowed:

- **Client names, engagement details, or paths to private/client repos** — in
  files, commit messages, branch names, tags, or issue/PR text.
- Anything derived from non-public bank specs or client-provided documents.
  Only publicly available bank specs are used (NDA safety).
- Client-owned code, even as a template.
- BRI documentation text copied verbatim, BRI logos/branding. README/UI must
  say: simulates BRI's publicly documented SNAP VA behaviour; not affiliated
  with or endorsed by PT Bank Rakyat Indonesia.
- Registration-gated documents (e.g. ASPI developer-site PDFs): link only.
- Sandbox credentials of any real bank.

## Conventions

- Java 25, Spring Boot 4, Maven, PostgreSQL (Flyway), Testcontainers for integration and image tests.
- No default values for required config (DB, bank key path, operator login,
  timeouts, skew):
  fail at startup with a clear message. Partner settings are required at
  registration; none are defaulted.
- Docker image built and pushed from this repo (Docker Hub, multi-arch).
- Commit messages end with the Co-Authored-By line given by the harness.

## Spec traceability

- `docs/spec-index.json` is the facts-only spec index: source pages with doc
  versions, endpoints, fields (type, M/O/C, length), headers, response codes,
  enums, signature formulas, and our assumptions (A-ids). No source prose.
- Code marks what it implements with `@SpecRef("<item id>")` or
  `@SpecRef("<item id>#<request|response>.<field>")`.
- A test enforces both directions: every `@SpecRef` resolves to an index entry,
  and every item with `"scope": "in"` has at least one `@SpecRef`.
- Tooling in `tools/spec/`:
  - `capture.mjs` (Playwright): captures every public source in the index
    into gitignored `docs/sources/<source-id>.md`.
    Run with `cd tools/spec && npm ci && npx playwright install chromium && npm run capture`.
  - `gen_index.py`: the index facts live in this script; it regenerates
    `docs/spec-index.json` and stamps each source's capture time and sha256.
  - `gen_index.py --check`: lists changed upstream pages and fails if the
    index is stale. When a page changes, diff its capture, update the facts in
    `gen_index.py`, and regenerate.
