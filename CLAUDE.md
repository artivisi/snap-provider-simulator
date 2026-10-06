# CLAUDE.md — SNAP Provider Simulator

Local simulator of BRI's SNAP (Standar Nasional Open API Pembayaran) VA side.
Public repo, Apache 2.0: an open-source product and a teaching tool for SNAP
integration training. README.md holds the original design (written for the
gateway use case). Status: design only, no code yet (as of 2026-10-06).
Plan: `docs/implementation-plan.md`.

Internal context (engagements, deadlines, private source locations) lives in
`CLAUDE.local.md`, which is gitignored. Nothing from it goes into tracked files.

## Consumers

1. SNAP integration training labs. Participants pull the Docker image; they do
   not build this repo. First run: image must be pullable by **12 Okt 2026**.
2. [payment-gateway](https://github.com/artivisi/payment-gateway): the
   self-hosted VA gateway. Same stack (Java 25, Spring Boot 4.1).
3. The payment-gateway YouTube series.

QRIS endpoints in README are out of scope for v0.1.

## Bank profile: BRI

The simulator emulates **BRI's publicly documented SNAP VA behaviour**. BRI
documents BOTH VA models (bank-hosted and biller-hosted), so one profile covers
every lab.

Source docs (portal is JS-rendered — open in a browser, capture field-level
request/response specs into `docs/bri/` as OUR OWN summaries before coding):

- OAuth: https://developers.bri.co.id/en/snap-bi/apidocs-oauth-snap-bi
- Create SNAP key: https://developers.bri.co.id/en/snap-bi/create-snap-key
- BRIVA, bank-hosted (partner → BRI): "Virtual Account/Transfer to VA SNAP BI
  v1.0" https://developers.bri.co.id/en/snap-bi/apidocs-virtual-account-transfer-va-snap-bi-v1.0
  (`/snap/v1.0/transfer-va/create-va` and siblings)
- BRIVA Online, biller-hosted (BRI → partner inquiry/payment):
  https://developers.bri.co.id/en/snap-bi/apidocs-virtual-accountbriva-online-snap-bi-v10

Match BRI's paths, headers, field names, VA number layout and response codes
as documented. Where the docs are silent, follow the public SNAP 1.0.2
standard and note the assumption in `docs/bri/`.

## What the training labs require

| Lab | Needs from simulator | BRI doc |
|---|---|---|
| 1 | B2B access token verifying SHA256withRSA over `clientId\|timestamp`; **diagnostic mode** returning the expected string-to-sign on failure (teaching aid, real BRI doesn't) | OAuth |
| 2 | HMAC-SHA512 symmetric signature verification on service calls, same diagnostic mode (expected string-to-sign, minified body hash) | OAuth |
| 3 | create / update / inquiry / delete VA; token TTL expiry to force re-auth | BRIVA (Transfer to VA) |
| 4 | **Bank-as-caller**: simulator gets a token FROM the partner app (signed with the simulator's bank private key), then calls the partner app's inquiry and payment endpoints. Triggered from admin UI/API ("customer pays at ATM") | BRIVA Online |
| 5 | Resend the same payment with the same `X-EXTERNAL-ID` | BRIVA Online |
| 6 | Failure injection: timeout after processing, slow response, invalid signature, late notification; plus payment status inquiry so participants resolve unknown outcomes | BRIVA (status/report endpoints as documented) |
| 7 | **Statement CSV export** (mutasi) per day incl. a payment whose notification was dropped, an amount mismatch, a duplicate. Format is ours (BRI statements are not part of this API) | — |

Also validate `X-TIMESTAMP` skew and `X-EXTERNAL-ID` uniqueness per day on
inbound calls.

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

- Java 25, Spring Boot 4, Maven, in-memory state.
- No default values for required config (client id, secret, key paths, partner
  base URL): fail at startup with a clear message.
- Docker image built and pushed from this repo; consumers pin a tag.
- Commit messages end with the Co-Authored-By line given by the harness.
