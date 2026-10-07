"""Generates docs/spec-index.json from facts recorded below plus the local captures.

Captures (docs/sources/<source-id>.md, gitignored) come from tools/spec/capture.mjs.
Each public source gets the sha256 and timestamp of its capture, so a re-capture
shows which upstream pages changed. BRIVA response code lists are parsed from
the captures; everything else is entered by hand from the sources.

Usage:
  python3 tools/spec/gen_index.py           write docs/spec-index.json
  python3 tools/spec/gen_index.py --check   exit 1 if the index differs from what
                                            the current captures produce
"""
import hashlib
import json
import re
import sys
from collections import OrderedDict
from pathlib import Path

REPO = str(Path(__file__).resolve().parents[2])
SRC = REPO + "/docs/sources"
INDEX = REPO + "/docs/spec-index.json"
CAPTURE_HEADER = re.compile(r"<!-- source: (\S+) (\S+) captured (\S+) -->")


def capture_meta(source):
    path = Path(SRC) / (source["id"] + ".md")
    if not path.is_file():
        raise SystemExit(f"missing capture {path}; run: cd tools/spec && npm run capture -- {source['id']}")
    header, body = path.read_text(encoding="utf-8").split("\n", 1)
    m = CAPTURE_HEADER.fullmatch(header)
    if not m or m.group(1) != source["id"] or m.group(2) != source["url"]:
        raise SystemExit(f"{path}: header does not match source {source['id']} {source['url']}")
    return m.group(3), hashlib.sha256(body.encode("utf-8")).hexdigest()


def f(name, type_, req, length, **extra):
    d = OrderedDict([("name", name), ("type", type_), ("req", req), ("length", length)])
    for k, v in extra.items():
        d[k] = v
    return d


def h(name, req, fmt, length, **extra):
    d = OrderedDict([("name", name), ("req", req), ("format", fmt), ("length", length)])
    for k, v in extra.items():
        d[k] = v
    return d


def c(source, value, field=None):
    d = OrderedDict()
    d["source"] = source
    if field:
        d["field"] = field
    d["value"] = value
    return d


def amount(prefix, req, vlen="16,2"):
    return [f(prefix, "Object", req, None),
            f(prefix + ".value", "String", "M", vlen),
            f(prefix + ".currency", "String", "M", "3")]


def rc(http, svc, case, message, **extra):
    d = OrderedDict([("code", f"{http}{svc}{case}"), ("http", http), ("case", case), ("message", message)])
    for k, v in extra.items():
        d[k] = v
    return d


MSG = {
    (200, "00"): "Successful",
    (400, "00"): "Bad Request",
    (400, "01"): "Invalid Field Format {field name}",
    (400, "02"): "Invalid Mandatory Field {field name}",
    (401, "00"): "Unauthorized. [reason]",
    (401, "01"): "Invalid Token (B2B)",
    (404, "01"): "Transaction Not Found",
    (404, "12"): "Invalid Bill/Virtual Account [Reason]",
    (404, "14"): "Paid Bill",
    (404, "16"): "Partner Not Found",
    (404, "19"): "Invalid Bill/Virtual Account",
    (409, "00"): "Conflict",
    (409, "01"): "Duplicate partnerReferenceNo",
    (500, "00"): "General Error",
    (504, "00"): "Timeout",
}
COMMON = [(200, "00"), (400, "00"), (400, "01"), (400, "02"), (401, "00"), (401, "01"),
          (404, "16"), (409, "00"), (500, "00"), (504, "00")]


def va_codes(svc, extra):
    keys = sorted(set(COMMON + extra), key=lambda k: (k[0], k[1]))
    return [rc(k[0], svc, k[1], MSG[k]) for k in keys]


def parse_briva_codes(path, svc):
    """Rows: http, service, case, message, status, req (v2 layout)."""
    rows = []
    in_section = False
    with open(path, encoding="utf-8") as fh:
        for line in fh:
            parts = line.rstrip("\n").split("\t")
            if len(parts) >= 6 and re.fullmatch(r"\d{3}", parts[0]) and parts[1] in (svc, "73") \
                    and re.fullmatch(r"\d{2}", parts[2]):
                if parts[1] == svc:
                    in_section = True
                if not in_section:
                    continue
                rows.append(parts[:6])
            elif in_section and rows and line.startswith("Any error"):
                break
    out = []
    for http, s, case, message, status, req in rows:
        if s == "73" and not in_section:
            continue
        msg = message.strip()
        extra = OrderedDict()
        if http == "200":
            extra_conf = [c("bri-briva-online-v2", msg)]
            msg = "Successful"
        else:
            extra_conf = None
        d = rc(int(http), s, case, msg, req=req.strip(), status=status.strip())
        if extra_conf:
            d["conflicts"] = extra_conf
        out.append(d)
    return out


# ---------------- sources ----------------
sources = [
    OrderedDict(id="bri-oauth", publisher="BRI", title="APIDOCS Oauth SNAP BI (Internal version v1.1)",
                url="https://developers.bri.co.id/en/snap-bi/apidocs-oauth-snap-bi", docVersion="v1.0",
                access="public"),
    OrderedDict(id="bri-briva-online-v2", publisher="BRI", title="APIDOCS Virtual Account Briva Online SNAP BI",
                url="https://developers.bri.co.id/en/snap-bi/apidocs-virtual-account-briva-online-snap-bi",
                docVersion="v2.5", access="public"),
    OrderedDict(id="bri-briva-online-v1", publisher="BRI", title="APIDOCS Virtual Account/Briva Online SNAP BI V1.0",
                url="https://developers.bri.co.id/en/snap-bi/apidocs-virtual-accountbriva-online-snap-bi-v10",
                docVersion="v2.4", access="public"),
    OrderedDict(id="bri-transfer-va", publisher="BRI", title="APIDOCS Virtual Account/Transfer to VA SNAP BI",
                url="https://developers.bri.co.id/en/snap-bi/apidocs-virtual-accounttransfer-va-snap-bi",
                docVersion="v2.3", access="public"),
    OrderedDict(id="bri-bank-statement", publisher="BRI", title="API Bank Statement SNAP BI",
                url="https://developers.bri.co.id/en/snap-bi/api-bank-statement-snap-bi",
                docVersion="v1.5", access="public"),
    OrderedDict(id="aspi-keamanan", publisher="ASPI", title="Keamanan",
                url="https://apidevportal.aspi-indonesia.or.id/api-services/keamanan",
                docVersion=None, access="public"),
    OrderedDict(id="aspi-va", publisher="ASPI", title="Transfer Kredit - Virtual Account",
                url="https://apidevportal.aspi-indonesia.or.id/api-services/transfer-kredit/virtual-account",
                docVersion=None, access="public"),
    OrderedDict(id="bri-briva-ws", publisher="BRI", title="BRIVA WS (bank-hosted VA)", url=None,
                docVersion=None, access="gated", used=False),
    OrderedDict(id="bri-va-status", publisher="BRI", title="VA transaction status", url=None,
                docVersion=None, access="gated", used=False),
    OrderedDict(id="aspi-aplikasi-pengujian", publisher="ASPI", title="Aplikasi Pengujian", url=None,
                docVersion=None, access="gated", used=False),
    OrderedDict(id="aspi-registered-pdf", publisher="ASPI", title="Registration-gated standard PDFs", url=None,
                docVersion=None, access="gated", used=False),
]

# ---------------- items ----------------
items = []


def item(id_, kind, scope, srcs, **rest):
    d = OrderedDict([("id", id_), ("kind", kind), ("scope", scope), ("sources", srcs)])
    for k, v in rest.items():
        d[k] = v
    d["assumptions"] = []
    items.append(d)
    return d


item("snap.headers.token", "header-set", "in", ["bri-oauth", "aspi-keamanan"],
     request=[
         h("Content-Type", "M", "application/json", None),
         h("X-CLIENT-KEY", "M", "Alphanumeric", None, conflicts=[c("aspi-keamanan", "String")]),
         h("X-TIMESTAMP", "M", "yyyy-MM-ddTHH:mm:ss.SSSTZD", None,
           conflicts=[c("aspi-keamanan", "yyyy-MM-ddTHH:mm:ssTZD")]),
         h("X-SIGNATURE", "M", "Base64(SHA256withRSA)", None,
           conflicts=[c("aspi-keamanan", "hex (code snippets)")]),
     ],
     response=[
         h("X-TIMESTAMP", "M", "yyyy-MM-ddTHH:mm:ssTZD", None),
         h("X-CLIENT-KEY", "M", "String", None),
     ])

item("snap.headers.service", "header-set", "in",
     ["bri-briva-online-v2", "bri-briva-online-v1", "bri-transfer-va", "bri-bank-statement", "aspi-keamanan"],
     request=[
         h("Content-Type", "M", "application/json", None),
         h("Authorization", "M", "Bearer {token}", None, conflicts=[c("aspi-keamanan", "C")]),
         h("X-TIMESTAMP", "M", "ISO-8601", None, conflicts=[c("aspi-keamanan", "yyyy-MM-ddTHH:mm:ssTZD")]),
         h("X-SIGNATURE", "M", "Base64(HMAC_SHA512)", None,
           conflicts=[c("aspi-keamanan", "hex (code snippets)")]),
         h("X-PARTNER-ID", "M", "Alphanumeric", "36", conflicts=[c("aspi-keamanan", "String 36")]),
         h("CHANNEL-ID", "M", "Numeric", "5",
           conflicts=[c("bri-bank-statement", "Alphanumeric 5"), c("aspi-keamanan", "String 5")]),
         h("X-EXTERNAL-ID", "M", "Numeric", "36",
           conflicts=[c("bri-transfer-va", "Numeric 9"), c("bri-bank-statement", "Numeric 9"),
                      c("aspi-keamanan", "String 36, numeric")]),
         h("ORIGIN", "O", "String", None),
     ],
     response=[
         h("Content-Type", "M", "application/json", None),
         h("X-TIMESTAMP", "M", "yyyy-MM-ddTHH:mm:ssTZD", None),
     ])

item("snap.sig.asymmetric-token", "rule", "in", ["bri-oauth", "aspi-keamanan"],
     formula='SHA256withRSA(privateKey, X-CLIENT-KEY + "|" + X-TIMESTAMP)')

item("snap.sig.symmetric", "rule", "in", ["bri-oauth", "aspi-keamanan"],
     formula='HMAC_SHA512(clientSecret, HTTPMethod + ":" + EndpointUrl + ":" + AccessToken + ":" + '
             'Lowercase(HexEncode(SHA-256(minify(RequestBody)))) + ":" + X-TIMESTAMP)',
     values=OrderedDict([
         ("httpMethod", ["GET", "POST", "PUT", "PATCH", "DELETE"]),
         ("endpointUrl", "path after host and port, without query string"),
         ("accessToken", "Authorization header value without \"Bearer \""),
     ]),
     conflicts=[c("aspi-keamanan", "relative path including URL parameters", field="endpointUrl"),
                c("bri-oauth", "SHA256-HMAC (prose)", field="algorithm")])

item("snap.sig.body-hash", "rule", "in", ["bri-oauth", "aspi-keamanan"],
     formula="Lowercase(HexEncode(SHA-256(minify(RequestBody))))",
     values=OrderedDict([
         ("emptyBody", "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"),
         ("checkInput", '{"hello":"world"}'),
         ("checkOutput", "93a23971a914e5eacbf0a8d25154cda309c3c1c72fbb9914d47c60f3cb681588"),
     ]),
     conflicts=[c("bri-oauth", "blank when no body", field="emptyBody")])

item("snap.sig.timestamp", "rule", "in", ["bri-oauth", "aspi-keamanan"],
     formula="yyyy-MM-ddTHH:mm:ss.SSSTZD",
     values=OrderedDict([
         ("accept", "ISO-8601 with offset (Z or +HH:mm), milliseconds optional"),
         ("generate", "yyyy-MM-dd'T'HH:mm:ss.SSS+07:00"),
         ("skew", "simulator.timestamp-skew"),
     ]),
     conflicts=[c("aspi-keamanan", "yyyy-MM-ddTHH:mm:ssTZD", field="formula"),
                c("bri-oauth", "UTC/GMT+0 required (prose); sample uses +07:00", field="offset")])

item("snap.response-code", "rule", "in", ["aspi-keamanan", "bri-briva-online-v2"],
     formula="responseCode = HTTP status (3) + service code (2) + case code (2)",
     values=OrderedDict([("responseCodeLength", "7"), ("responseMessageLength", "150")]))

item("snap.external-id", "rule", "in", ["aspi-keamanan", "bri-briva-online-v2"],
     formula="unique per (client id, calendar day Asia/Jakarta); duplicate -> 409xx00 Conflict",
     values=OrderedDict([("inbound", "Numeric 1-36"), ("outbound", "Numeric 32")]))

item("bri.va.number-layout", "rule", "in", ["bri-briva-online-v2", "bri-transfer-va", "aspi-va"],
     formula="virtualAccountNo = partnerServiceId (8, left-padded with spaces) + customerNo",
     values=OrderedDict([("partnerServiceId", "8"), ("customerNo", "13"), ("virtualAccountNo", "28")]),
     conflicts=[c("aspi-va", "20", field="customerNo"),
                c("bri-briva-online-v2", "left padding 0 (virtualAccountNo row)", field="padding")])

# token endpoints
token_req = [f("grantType", "String", "M", None, values=["client_credentials"]),
             f("additionalInfo", "Object", "O", None)]
token_resp = [f("responseCode", "String", "C", None),
              f("responseMessage", "String", "C", None),
              f("accessToken", "String", "M", None, conflicts=[c("aspi-keamanan", "2048")]),
              f("tokenType", "String", "M", None, values=["BearerToken"],
                conflicts=[c("aspi-keamanan", "Bearer, Mac")]),
              f("expiresIn", "String", "M", None, values=["900"]),
              f("additionalInfo", "Object", "O", None)]

item("bri.oauth.token-b2b", "endpoint", "in", ["bri-oauth", "aspi-keamanan"],
     direction="partner-to-bank", method="POST", path="/snap/v1.0/access-token/b2b", serviceCode="73",
     headers="snap.headers.token", request=token_req, response=token_resp,
     responseCodes=[
         OrderedDict([("code", None), ("http", 200), ("case", None), ("message", None)]),
         rc(400, "73", "00", "Bad Request"),
         rc(400, "73", "01", "Invalid Field Format {field name}"),
         rc(400, "73", "02", "Invalid Mandatory Field {field name}"),
         rc(401, "73", "00", "Unauthorized. [reason]",
            conflicts=[c("bri-oauth", "Unauthorized Client / Unauthorized stringToSign / Unauthorized Signature")]),
         rc(401, "73", "01", "Invalid Token (B2B)"),
         rc(500, "73", "00", "General Error", conflicts=[c("bri-oauth", "500000")]),
     ])

item("aspi.oauth.token-b2b-outbound", "endpoint", "in", ["aspi-keamanan", "bri-oauth"],
     direction="bank-to-partner", method="POST", path="/v1.0/access-token/b2b", serviceCode="73",
     headers="snap.headers.token", request=token_req, response=token_resp,
     responseCodes=[rc(200, "73", "00", "Successful"),
                    rc(400, "73", "00", "Bad Request"),
                    rc(400, "73", "01", "Invalid Field Format {field name}"),
                    rc(400, "73", "02", "Invalid Mandatory Field {field name}"),
                    rc(401, "73", "00", "Unauthorized. [reason]"),
                    rc(500, "73", "00", "General Error"),
                    rc(504, "73", "00", "Timeout")])

# ---- bank-hosted VA (ASPI) ----
ID_REQ_M = [f("partnerServiceId", "String", "M", "8"), f("customerNo", "String", "M", "20"),
            f("virtualAccountNo", "String", "M", "28")]


def vad(fields):
    return [f("virtualAccountData." + x["name"], x["type"], x["req"], x["length"],
              **{k: v for k, v in x.items() if k not in ("name", "type", "req", "length")}) for x in fields]


def bill_details(prefix, status_extras=False):
    p = prefix + "billDetails[]"
    out = [f(p, "Array of Objects", "O", "24"),
           f(p + ".billCode", "String", "O", "2"), f(p + ".billNo", "String", "O", "18"),
           f(p + ".billName", "String", "O", "20"), f(p + ".billShortName", "String", "O", "10"),
           f(p + ".billDescription", "Object", "O", None),
           f(p + ".billDescription.english", "String", "O", "18"),
           f(p + ".billDescription.indonesia", "String", "O", "18"),
           f(p + ".billSubCompany", "String", "O", "5")]
    out += amount(p + ".billAmount", "O")
    out.append(f(p + ".additionalInfo", "Object", "O", "unlimited"))
    if status_extras:
        out += [f(p + ".billReferenceNo", "Number", "O", "15"), f(p + ".status", "String", "O", "2"),
                f(p + ".reason", "Object", "O", "2"), f(p + ".reason.english", "String", "O", "64"),
                f(p + ".reason.indonesia", "String", "O", "64")]
    return out


def free_texts(prefix):
    p = prefix + "freeTexts[]"
    return [f(p, "Array of Objects", "O", "25"), f(p + ".english", "String", "O", "32"),
            f(p + ".indonesia", "String", "O", "32")]


def fee_amount(prefix, array_conflict=False):
    first = f(prefix + "feeAmount", "Object", "O", None)
    if array_conflict:
        first["conflicts"] = [c("aspi-va", "Array of Objects (create-va request)")]
    return [first, f(prefix + "feeAmount.value", "String", "M", "16,2"),
            f(prefix + "feeAmount.currency", "String", "M", "3")]


TRX_TYPE = f("virtualAccountTrxType", "String", "O", "1", values=["C", "O", "I", "M", "L", "N", "X"])
RESP_HEAD = [f("responseCode", "String", "M", "7"), f("responseMessage", "String", "M", "150"),
             f("virtualAccountData", "Object", "M", None)]
CONTACT = [f("virtualAccountName", "String", "M", "255"), f("virtualAccountEmail", "String", "O", "255"),
           f("virtualAccountPhone", "String", "O", "30")]
DATES = [f("expiredDate", "String", "O", "25"), f("lastUpdateDate", "String", "O", "25"),
         f("paymentDate", "String", "O", "25")]

create_req = ([f("partnerServiceId", "String", "O", "8"), f("customerNo", "String", "O", "20"),
               f("virtualAccountNo", "String", "O", "28")] + CONTACT + [f("trxId", "String", "M", "64")]
              + amount("totalAmount", "O") + bill_details("") + free_texts("") + [TRX_TYPE]
              + fee_amount("", array_conflict=True) + [f("expiredDate", "String", "O", "25"),
                                                       f("additionalInfo", "Object", "O", None)])
create_resp = RESP_HEAD + vad(ID_REQ_M + CONTACT + [f("trxId", "String", "M", "32")] + amount("totalAmount", "O")
                              + bill_details("") + free_texts("") + [TRX_TYPE] + fee_amount("")
                              + [f("expiredDate", "String", "O", "25"), f("additionalInfo", "Object", "O", None)])

update_req = (ID_REQ_M + CONTACT + [f("trxId", "String", "M", "64")] + amount("totalAmount", "O")
              + bill_details("") + free_texts("") + [TRX_TYPE] + fee_amount("")
              + [f("expiredDate", "String", "O", "25"), f("additionalInfo", "Object", "O", None)])
update_resp = RESP_HEAD + vad(ID_REQ_M + CONTACT + [f("trxId", "String", "M", "32")] + amount("totalAmount", "O")
                              + bill_details("") + [TRX_TYPE] + fee_amount("") + DATES
                              + [f("additionalInfo", "Object", "O", None)])

inquiry_req = ID_REQ_M + [f("trxId", "String", "M", "64")]
inquiry_resp = RESP_HEAD + vad(ID_REQ_M + CONTACT + [f("trxId", "String", "M", "32")] + amount("totalAmount", "O")
                               + bill_details("") + free_texts("") + [TRX_TYPE] + fee_amount("") + DATES
                               + [f("additionalInfo", "Object", "O", None)])

delete_req = ID_REQ_M + [f("trxId", "String", "O", "64"), f("additionalInfo", "Object", "O", "unlimited")]
delete_resp = RESP_HEAD + vad(ID_REQ_M + [f("trxId", "String", "O", "12"),
                                          f("additionalInfo", "Object", "O", "unlimited")])

status_req = ID_REQ_M + [f("inquiryRequestId", "String", "C", "128"), f("paymentRequestId", "String", "O", "128"),
                         f("additionalInfo", "Object", "O", None)]
status_resp = RESP_HEAD + vad(
    [f("paymentFlagReason", "Object", "O", None), f("paymentFlagReason.english", "String", "O", "200"),
     f("paymentFlagReason.indonesia", "String", "O", "200")]
    + ID_REQ_M + [f("inquiryRequestId", "String", "M", "128"), f("paymentRequestId", "String", "C", "128")]
    + amount("paidAmount", "O") + [f("paidBills", "String", "O", "6")] + amount("totalAmount", "O")
    + [f("trxDateTime", "Date", "O", "25"), f("transactionDate", "Date", "O", "25"),
       f("referenceNo", "String", "O", "15"), f("paymentType", "String", "O", "1"),
       f("flagAdvise", "String", "O", "1"), f("paymentFlagStatus", "String", "O", "2")]
    + bill_details("", status_extras=True) + free_texts("") + [f("additionalInfo", "Object", "O", None)])

updstat_req = ID_REQ_M + [f("trxId", "String", "M", "64"), f("paidStatus", "String", "M", "1", values=["Y", "N"])]
updstat_resp = RESP_HEAD + vad(ID_REQ_M + CONTACT + [f("trxId", "String", "M", "64")] + amount("totalAmount", "O")
                               + [TRX_TYPE] + fee_amount("") + DATES + [f("additionalInfo", "Object", "O", None)])

report_req = [f("partnerServiceId", "Number", "M", "8"), f("startDate", "String", "O", "10"),
              f("startTime", "String", "O", "14"), f("endDate", "String", "O", "10"),
              f("endTime", "String", "O", "14"), f("additionalInfo", "Object", "O", "unlimited")]
rp = "virtualAccountData[]."
report_resp = ([f("responseCode", "String", "M", "7"), f("responseMessage", "String", "M", "150"),
                f("virtualAccountData[]", "Array of Objects", "M", None,
                  conflicts=[c("aspi-va", "virtualAccountdata")]),
                f(rp + "paymentFlagReason", "Object", "O", None),
                f(rp + "paymentFlagReason.english", "String", "O", "200"),
                f(rp + "paymentFlagReason.indonesia", "String", "O", "200"),
                f(rp + "partnerServiceId", "String", "M", "8"), f(rp + "customerNo", "String", "M", "20"),
                f(rp + "virtualAccountNo", "String", "M", "28"), f(rp + "virtualAccountName", "String", "M", "255"),
                f(rp + "virtualAccountEmail", "String", "O", "255"), f(rp + "virtualAccountPhone", "String", "O", "30"),
                f(rp + "sourceAccountNo", "String", "O", "32"),
                f(rp + "sourceAccountType", "String", "O", "1", values=["D", "S"]),
                f(rp + "trxId", "String", "O", "64"), f(rp + "inquiryRequestId", "String", "O", "128"),
                f(rp + "paymentRequestId", "String", "O", "128")]
               + amount(rp + "paidAmount", "O") + [f(rp + "paidBills", "String", "O", "6")]
               + amount(rp + "totalAmount", "O")
               + [f(rp + "trxDateTime", "Date", "O", "25"), f(rp + "referenceNo", "String", "O", "15"),
                  f(rp + "journalNum", "String", "O", "6"), f(rp + "paymentType", "String", "O", "1"),
                  f(rp + "flagAdvise", "String", "O", "1")]
               + bill_details(rp, status_extras=False)
               + [f(rp + "billDetails[].status", "String", "O", "2"),
                  f(rp + "billDetails[].reason", "String", "O", "2"),
                  f(rp + "billDetails[].reason.english", "String", "O", "64"),
                  f(rp + "billDetails[].reason.indonesia", "String", "O", "64")]
               + free_texts(rp) + [f(rp + "additionalInfo", "Object", "O", "unlimited")])

aspi_path_conf = lambda p: [c("aspi-va", ".../{version}/transfer-va/" + p, field="path")]

item("aspi.va.create-va", "endpoint", "in", ["aspi-va"], direction="partner-to-bank", method="POST",
     path="/snap/v1.0/transfer-va/create-va", serviceCode="27", headers="snap.headers.service",
     request=create_req, response=create_resp, responseCodes=va_codes("27", [(409, "01")]),
     conflicts=aspi_path_conf("create-va"))
item("aspi.va.update-va", "endpoint", "in", ["aspi-va"], direction="partner-to-bank", method="PUT",
     path="/snap/v1.0/transfer-va/update-va", serviceCode="28", headers="snap.headers.service",
     request=update_req, response=update_resp,
     responseCodes=va_codes("28", [(404, "12"), (404, "14"), (404, "19")]),
     conflicts=aspi_path_conf("update-va"))
item("aspi.va.inquiry-va", "endpoint", "in", ["aspi-va"], direction="partner-to-bank", method="POST",
     path="/snap/v1.0/transfer-va/inquiry-va", serviceCode="30", headers="snap.headers.service",
     request=inquiry_req, response=inquiry_resp, responseCodes=va_codes("30", [(404, "12"), (404, "19")]),
     conflicts=aspi_path_conf("inquiry-va"))
item("aspi.va.delete-va", "endpoint", "in", ["aspi-va"], direction="partner-to-bank", method="DELETE",
     path="/snap/v1.0/transfer-va/delete-va", serviceCode="31", headers="snap.headers.service",
     request=delete_req, response=delete_resp, responseCodes=va_codes("31", [(404, "12"), (404, "14")]),
     conflicts=aspi_path_conf("delete-va"))
item("aspi.va.inquiry-status", "endpoint", "in", ["aspi-va"], direction="partner-to-bank", method="POST",
     path="/snap/v1.0/transfer-va/status", serviceCode="26", headers="snap.headers.service",
     request=status_req, response=status_resp, responseCodes=va_codes("26", [(404, "01"), (404, "12")]),
     conflicts=aspi_path_conf("status"))
item("aspi.va.update-status-va", "endpoint", "out", ["aspi-va"], direction="partner-to-bank", method="PUT",
     path="/snap/v1.0/transfer-va/update-status", serviceCode="29", headers="snap.headers.service",
     request=updstat_req, response=updstat_resp, responseCodes=[],
     conflicts=aspi_path_conf("update-status"))
item("aspi.va.get-report", "endpoint", "out", ["aspi-va"], direction="partner-to-bank", method="GET",
     path="/snap/v1.0/transfer-va/report", serviceCode="35", headers="snap.headers.service",
     request=report_req, response=report_resp, responseCodes=[],
     conflicts=aspi_path_conf("report") + [c("aspi-va", "POST (code snippet)", field="method")])

# ---- BRIVA Online (bank -> partner) ----
V2 = SRC + "/bri-briva-online-v2.md"
ID_BRI = [f("partnerServiceId", "String", "M", "8"),
          f("customerNo", "String", "M", "13", conflicts=[c("aspi-va", "20")]),
          f("virtualAccountNo", "String", "M", "28")]

inq_req = ID_BRI + amount("amount", "O") + [
    f("trxDateInit", "Date", "O", "25"), f("channelCode", "Integer", "O", "4"),
    f("sourceBankCode", "String", "O", "3"), f("passApp", "String", "O", "64"),
    f("inquiryRequestId", "String", "M", "36", conflicts=[c("aspi-va", "128")]),
    f("additionalInfo", "Object", "O", None), f("additionalInfo.idApp", "String", "M", "8")]
inq_resp = [f("responseCode", "String", "M", "7"), f("responseMessage", "String", "M", "150"),
            f("virtualAccountData", "Object", "M", None)] + vad(
    [f("partnerServiceId", "String", "M", "8"),
     f("customerNo", "String", "M", "13", conflicts=[c("aspi-va", "20")]),
     f("virtualAccountNo", "String", "M", "18", conflicts=[c("aspi-va", "28")]),
     f("virtualAccountName", "String", "M", "255"), f("inquiryRequestId", "String", "M", "128"),
     f("totalAmount", "Object", "M", None, conflicts=[c("aspi-va", "O")]),
     f("totalAmount.value", "String", "M", "16,2"), f("totalAmount.currency", "String", "M", "3"),
     f("inquiryStatus", "String", "O", "2"), f("inquiryReason", "Object", "O", None),
     f("inquiryReason.english", "String", "O", "64"), f("inquiryReason.indonesia", "String", "O", "64"),
     f("additionalInfo", "Object", "O", None, conflicts=[c("bri-briva-online-v2", "String (table)")]),
     f("additionalInfo.idApp", "String", "M", "8"), f("additionalInfo.info1", "String", "O", "20")])

pay_req = ID_BRI + [f("virtualAccountName", "String", "M", "255")] + amount("paidAmount", "M") + [
    f("trxDateTime", "Date", "O", "25"), f("channelCode", "Integer", "O", "4"),
    f("sourceBankCode", "String", "O", "3"), f("trxId", "String", "C", "64"),
    f("paymentRequestId", "String", "M", "36", conflicts=[c("aspi-va", "128")]),
    f("hashedSourceAccountNo", "String", "O", "32"),
    f("additionalInfo", "Object", "O", None), f("additionalInfo.passApp", "String", "O", "64"),
    f("additionalInfo.idApp", "String", "O", "8")]
pay_resp = [f("responseCode", "String", "M", "7"), f("responseMessage", "String", "M", "150"),
            f("virtualAccountData", "Object", "M", None)] + vad(
    [f("partnerServiceId", "String", "M", "8"),
     f("customerNo", "String", "M", "13", conflicts=[c("aspi-va", "20")]),
     f("virtualAccountNo", "String", "M", "28"), f("virtualAccountName", "String", "M", "255"),
     f("paymentRequestId", "String", "M", "36", conflicts=[c("aspi-va", "128")])]
    + amount("paidAmount", "O")
    + [f("paymentFlagStatus", "String", "O", "2", values=["00", "01", "02"],
         conflicts=[c("bri-briva-online-v2", "Object (table)")]),
       f("paymentFlagReason", "Object", "O", None, conflicts=[c("bri-briva-online-v2", "String (table)")]),
       f("paymentFlagReason.english", "String", "O", None),
       f("paymentFlagReason.indonesia", "String", "O", None)]) + [
    f("additionalInfo", "Object", "O", None,
      conflicts=[c("bri-briva-online-v1", "virtualAccountData.additionalInfo (sample)")]),
    f("additionalInfo.passApp", "String", "O", "64"), f("additionalInfo.idApp", "String", "M", "8"),
    f("additionalInfo.info1", "String", "O", "20")]

inq_codes = parse_briva_codes(V2, "24")
pay_codes = parse_briva_codes(V2, "25")
for codes, svc in ((inq_codes, "24"), (pay_codes, "25")):
    for r in codes:
        if r["code"] == f"404{svc}12":
            r["conflicts"] = [c("bri-briva-online-v1", "Invalid Bill/Virtual Account not Found")]

item("bri.briva-online.inquiry", "endpoint", "in", ["bri-briva-online-v2", "bri-briva-online-v1", "aspi-va"],
     direction="bank-to-partner", method="POST", path="/v1.0/transfer-va/inquiry", serviceCode="24",
     headers="snap.headers.service", request=inq_req, response=inq_resp, responseCodes=inq_codes,
     conflicts=[c("bri-briva-online-v2", ".../{{version}}/transfer-va/inquiry", field="path"),
                c("bri-briva-online-v1", "snap/v1.0/transfer-va/inquiry", field="path")])
item("bri.briva-online.payment", "endpoint", "in", ["bri-briva-online-v2", "bri-briva-online-v1", "aspi-va"],
     direction="bank-to-partner", method="POST", path="/v1.0/transfer-va/payment", serviceCode="25",
     headers="snap.headers.service", request=pay_req, response=pay_resp, responseCodes=pay_codes,
     conflicts=[c("bri-briva-online-v2", "../{{version}}/transfer-va/payment", field="path"),
                c("bri-briva-online-v1", "snap/v1.0/transfer-va/payment", field="path")])

# ---- enums ----
def ev(code, label):
    return OrderedDict([("code", code), ("label", label)])


item("bri.channel-id", "enum", "in", ["bri-briva-online-v2", "bri-briva-online-v1"],
     values=[ev("00001", "teller"), ev("00002", "ATM"), ev("00003", "IB/NBMB/Brilink Mobile"),
             ev("00004", "SMSB"), ev("00005", "CMS/IBIZ"), ev("00006", "EDC"), ev("00007", "RTGS"),
             ev("00008", "OTHER"), ev("00009", "API")])
item("bri.payment-flag-status", "enum", "in", ["bri-briva-online-v2", "bri-briva-online-v1"],
     values=[ev("00", "Success"), ev("01", "Reject"), ev("02", "Timeout")])
item("aspi.va.trx-type", "enum", "in", ["aspi-va"],
     values=[ev("C", "Closed Payment"), ev("O", "Open Payment"), ev("I", "Partial"), ev("M", "Minimum"),
             ev("L", "Maximum"), ev("N", "Open Minimum"), ev("X", "Open Maximum")])
item("aspi.va.paid-status", "enum", "out", ["aspi-va"], values=[ev("Y", "Paid"), ev("N", "Not Paid")])

# ---- bank statement (reference) ----
dd = "detailData[]."
stmt_resp = ([f("responseCode", "String", "M", "7"), f("responseMessage", "String", "M", "150"),
              f("referenceNo", "String", "C", "64"),
              f("totalCreditEntries", "Object", "O", None),
              f("totalCreditEntries.numberOfEntries", "Integer", "O", "5")]
             + amount("totalCreditEntries.amount", "M", "15,2")
             + [f("totalDebitEntries", "Object", "O", None),
                f("totalDebitEntries.numberOfEntries", "Integer", "O", "5")]
             + amount("totalDebitEntries.amount", "M", "15,2")
             + [f("detailData[]", "Array of Objects", None, None),
                f(dd + "detailBalance", "Object", "O", None),
                f(dd + "detailBalance.startAmount", "Object", "O", None,
                  conflicts=[c("bri-bank-statement", "Array (sample)")]),
                f(dd + "detailBalance.startAmount.amount", "Object", "O", None),
                f(dd + "detailBalance.startAmount.amount.value", "String", "M", "15,2"),
                f(dd + "detailBalance.startAmount.amount.currency", "String", "M", "3"),
                f(dd + "detailBalance.endAmount", "Object", "O", None,
                  conflicts=[c("bri-bank-statement", "Array (sample)")]),
                f(dd + "detailBalance.endAmount.amount", "Object", "O", None),
                f(dd + "detailBalance.endAmount.amount.value", "String", "M", "15,2"),
                f(dd + "detailBalance.endAmount.amount.currency", "String", "M", "3")]
             + amount(dd + "amount", "O", "15,2")
             + [f(dd + "transactionDate", "String", "M", "25"), f(dd + "remark", "String", "M", "256"),
                f(dd + "transactionId", "String", "O", "35"),
                f(dd + "type", "String", "M", "6", values=["CREDIT", "DEBIT"],
                  conflicts=[c("bri-bank-statement", "Credit (sample)")])])
item("bri.bank-statement", "endpoint", "out", ["bri-bank-statement"], direction="partner-to-bank",
     method="POST", path="/snap/v1.1/bank-statement", serviceCode="14", headers="snap.headers.service",
     request=[f("accountNo", "String", "M", "15", conflicts=[c("bri-bank-statement", "SNAP BI: C, 16")]),
              f("fromDateTime", "String", "M", "25", conflicts=[c("bri-bank-statement", "SNAP BI: O")]),
              f("toDateTime", "String", "M", "25", conflicts=[c("bri-bank-statement", "SNAP BI: O")])],
     response=stmt_resp,
     responseCodes=[rc(200, "14", "00", "Successfull"), rc(400, "14", "01", "Invalid Field Format {fieldName}"),
                    rc(400, "14", "02", "Invalid Mandatory Field {fieldName}"),
                    rc(401, "14", "00", "Unauthorized. Client"), rc(404, "14", "01", "Transaction not found"),
                    rc(404, "14", "11", "Invalid Card/Account/Customer [Account No cannot be found]"),
                    rc(409, "14", "00", "Conflict"), rc(500, "14", "00", "General Error"),
                    rc(504, "14", "00", "Timeout")])

# ---- simulator's own statement export ----
def col(name, from_, fmt):
    return OrderedDict([("name", name), ("from", from_), ("format", fmt)])


item("sim.statement-csv", "rule", "in", [],
     formula="one CSV per day: ledger credits of that day sorted by transaction_date",
     values=OrderedDict([
         ("path", "GET /admin/statements/{yyyy-MM-dd}.csv"),
         ("columns", [
             col("transaction_date", "bri.bank-statement#response.detailData[].transactionDate",
                 "yyyy-MM-dd'T'HH:mm:ss+07:00"),
             col("transaction_id", "bri.bank-statement#response.detailData[].transactionId", "digits"),
             col("type", "bri.bank-statement#response.detailData[].type", "CREDIT"),
             col("amount", "bri.bank-statement#response.detailData[].amount.value", "0.00"),
             col("currency", "bri.bank-statement#response.detailData[].amount.currency", "IDR"),
             col("virtual_account_no", None, "digits, padding spaces removed"),
             col("remark", "bri.bank-statement#response.detailData[].remark", "BRIVA <vaNo> <name>"),
         ]),
         ("encoding", "UTF-8"), ("separator", ","), ("headerRow", True), ("lineEnding", "\n"),
         ("quoting", "RFC 4180"), ("totalRow", False), ("day", "00:00-24:00 Asia/Jakarta"),
     ]))

# ---------------- assumptions ----------------
VA_IN = ["aspi.va.create-va", "aspi.va.update-va", "aspi.va.inquiry-va", "aspi.va.delete-va",
         "aspi.va.inquiry-status"]
BRIVA = ["bri.briva-online.inquiry", "bri.briva-online.payment"]
A = [
    ("A1", ["snap.sig.asymmetric-token", "snap.sig.symmetric", "snap.headers.token", "snap.headers.service"],
     "X-SIGNATURE (RSA and HMAC) is standard Base64 of the signature bytes."),
    ("A2", ["snap.sig.symmetric"], "Symmetric signature algorithm is HMAC-SHA512."),
    ("A3", ["snap.sig.symmetric"], "EndpointUrl in the string-to-sign is the path without query string."),
    ("A4", ["snap.sig.body-hash"], "An empty body hashes as SHA-256 of the empty string."),
    ("A5", ["snap.sig.timestamp", "snap.headers.token", "snap.headers.service"],
     "X-TIMESTAMP is accepted as ISO-8601 with an explicit offset, milliseconds optional; no offset is rejected with 400xx01."),
    ("A6", ["snap.sig.timestamp"], "Allowed clock skew is required config; outside it returns 401xx00."),
    ("A7", ["snap.sig.timestamp"], "Generated timestamps use yyyy-MM-dd'T'HH:mm:ss.SSS+07:00."),
    ("A8", ["snap.external-id", "snap.headers.service"],
     "Inbound X-EXTERNAL-ID is 1-36 digits; outbound is 32 random digits."),
    ("A9", ["snap.external-id"],
     "X-EXTERNAL-ID is unique per client id and Jakarta calendar day across all services; reuse returns 409xx00."),
    ("A10", ["snap.headers.service"],
     "Inbound X-PARTNER-ID is required, alphanumeric up to 36, format-checked only."),
    ("A11", ["snap.headers.service", "bri.channel-id"],
     "Inbound CHANNEL-ID must be 5 digits and is not checked against the list; outbound must be from the list."),
    ("A12", ["bri.oauth.token-b2b"],
     "Token success body has accessToken, tokenType BearerToken and expiresIn only, no responseCode."),
    ("A13", ["snap.headers.token", "snap.headers.service"],
     "Responses carry X-TIMESTAMP; token responses also carry X-CLIENT-KEY."),
    ("A14", ["bri.oauth.token-b2b"],
     "Each token request issues a new token; earlier tokens stay valid until their own expiry; no rate limit."),
    ("A15", ["snap.headers.service"] + VA_IN,
     "Missing, unknown or expired bearer token returns 401xx01."),
    ("A16", ["bri.oauth.token-b2b"],
     "Missing mandatory token-call header returns 4007302; General Error is 5007300."),
    ("A17", ["bri.va.number-layout"],
     "partnerServiceId is 8 chars space-padded, customerNo 1-13 digits, virtualAccountNo their concatenation."),
    ("A18", ["bri.va.number-layout"] + VA_IN,
     "Inbound partnerServiceId must equal the calling partner's registered value, else 404xx16."),
    ("A19", ["aspi.va.create-va"],
     "create-va requires partnerServiceId, customerNo, virtualAccountNo and totalAmount."),
    ("A20", ["aspi.va.create-va", "aspi.va.trx-type"],
     "Only closed single-payment VAs: virtualAccountTrxType absent or C, otherwise 400xx01."),
    ("A21", ["aspi.va.create-va", "aspi.va.update-va", "aspi.va.inquiry-va"],
     "expiredDate is optional and must carry an offset; expired VA returns 404xx19."),
    ("A22", ["aspi.va.create-va"], "Active VA number or reused trxId on create-va returns 4092701."),
    ("A23", ["aspi.va.update-va", "aspi.va.inquiry-va", "aspi.va.delete-va"],
     "Lookup by virtualAccountNo; trxId mismatch or unknown VA returns 404xx12; paid VA on update/delete returns 404xx14."),
    ("A24", ["aspi.va.inquiry-status"],
     "Match by virtualAccountNo then paymentRequestId; paid 2002600 flag 00, unpaid 4042601, unknown 4042612; one object returned."),
    ("A25", ["bri.briva-online.payment"],
     "Bank-hosted VA payment is notified with the same payment call, carrying the create-va trxId."),
    ("A26", BRIVA + ["aspi.oauth.token-b2b-outbound"],
     "Partner endpoints are {base-url}/v1.0/...; any prefix belongs in the base URL."),
    ("A27", ["aspi.oauth.token-b2b-outbound"],
     "The bank gets its partner token via the B2B flow, X-CLIENT-KEY = bank client id, RSA-signed with the bank key."),
    ("A28", BRIVA + ["bri.channel-id"],
     "Outbound X-PARTNER-ID is the bank client id the partner issued; CHANNEL-ID is a required parameter of the payment action; channelCode is its integer value."),
    ("A29", BRIVA,
     "Outbound sends sourceBankCode 002, inquiry amount 0.00 IDR, no passApp/idApp/hashedSourceAccountNo; paymentRequestId equals inquiryRequestId."),
    ("A30", ["bri.briva-online.payment", "bri.payment-flag-status"],
     "additionalInfo is parsed at top level or inside virtualAccountData; paymentFlagStatus is read as a string."),
    ("A31", ["bri.briva-online.payment", "bri.payment-flag-status"],
     "2002500 with flag 00 credits; listed 4xx or flag 01 reverses; 429/5xx/timeout/unlisted or flag 02 suspends with credit kept."),
    ("A32", ["sim.statement-csv"], "Statement transaction_id is a bank journal id distinct from the partner trxId."),
    ("A33", ["sim.statement-csv", "snap.external-id"], "Day boundary is 00:00-24:00 Asia/Jakarta."),
    ("A34", VA_IN + ["aspi.va.update-status-va", "aspi.va.get-report"],
     "Bank-hosted VA paths are the ASPI paths under BRI's /snap/v1.0 prefix."),
]
assumptions = [OrderedDict([("id", a), ("items", its), ("decision", d)]) for a, its, d in A]

by_id = {i["id"]: i for i in items}
for a in assumptions:
    for it in a["items"]:
        by_id[it]["assumptions"].append(a["id"])

for src in sources:
    if src["access"] == "public":
        src["captured"], src["contentSha256"] = capture_meta(src)

doc = OrderedDict([("schemaVersion", 1), ("sources", sources),
                   ("items", items), ("assumptions", assumptions)])
rendered = json.dumps(doc, indent=2, ensure_ascii=False) + "\n"

if sys.argv[1:] == ["--check"]:
    committed = json.loads(Path(INDEX).read_text(encoding="utf-8"))
    old = {x["id"]: x.get("contentSha256") for x in committed["sources"]}
    changed = [x["id"] for x in sources if x.get("contentSha256") and old.get(x["id"]) != x["contentSha256"]]
    for sid in changed:
        print(f"upstream changed: {sid}; diff the capture against the index and update the facts here")
    if rendered != Path(INDEX).read_text(encoding="utf-8"):
        print("docs/spec-index.json is out of date")
        sys.exit(1)
    print("spec index up to date")
elif sys.argv[1:]:
    raise SystemExit("usage: gen_index.py [--check]")
else:
    Path(INDEX).write_text(rendered, encoding="utf-8")
    print(len(items), "items")
