package com.artivisi.snapsimulator.functional;

import com.artivisi.snapsimulator.SpecRef;
import com.artivisi.snapsimulator.snap.SnapSignature;
import com.artivisi.snapsimulator.snap.SnapTimestamp;
import com.artivisi.snapsimulator.support.SnapTestClient;
import com.microsoft.playwright.APIResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.Map;

import static com.artivisi.snapsimulator.functional.AccessTokenFunctionalTest.assertCode;
import static org.assertj.core.api.Assertions.assertThat;

/** Check chain on a service call, exercised on inquiry-va (service code 30). */
@SpecRef("snap.headers.service")
class ServiceCheckChainFunctionalTest extends PlaywrightTestBase {

    private static final String PATH = "/snap/v1.0/transfer-va/inquiry-va";
    private static final String BODY = "{\n  \"partnerServiceId\": \"   10001\",\n  \"virtualAccountNo\": \"   100011\"\n}";

    @Test
    @DisplayName("Missing, unknown and expired bearer tokens return 4013001")
    void invalidToken() {
        TestPartner partner = signupWithKey(false, 10);
        SnapTestClient client = snapClient(partner);
        Map<String, String> headers = client.serviceHeaders("POST", PATH, "x", BODY);
        headers.remove("Authorization");
        assertCode(client.call("POST", PATH, headers, BODY), "4013001", "Invalid Token (B2B)");
        assertCode(client.call("POST", PATH, "not-a-token", BODY), "4013001", "Invalid Token (B2B)");
    }

    @Test
    @DisplayName("Each missing mandatory header returns 4003002; malformed ones 4003001")
    void headerChecks() {
        TestPartner partner = signupWithKey(false, 300);
        SnapTestClient client = snapClient(partner);
        String token = client.obtainToken();
        for (String header : new String[] {"X-TIMESTAMP", "X-SIGNATURE", "X-PARTNER-ID", "CHANNEL-ID", "X-EXTERNAL-ID"}) {
            Map<String, String> headers = client.serviceHeaders("POST", PATH, token, BODY);
            headers.remove(header);
            assertCode(client.call("POST", PATH, headers, BODY), "4003002", "Invalid Mandatory Field " + header);
        }
        Map<String, String[]> malformed = Map.of(
                "X-PARTNER-ID", new String[] {"has space"},
                "CHANNEL-ID", new String[] {"9522"},
                "X-EXTERNAL-ID", new String[] {"12ab"},
                "X-TIMESTAMP", new String[] {"2026-10-08 10:00"});
        malformed.forEach((header, value) -> {
            Map<String, String> headers = client.serviceHeaders("POST", PATH, token, BODY);
            headers.put(header, value[0]);
            assertCode(client.call("POST", PATH, headers, BODY), "4003001", "Invalid Field Format " + header);
        });
    }

    @Test
    @SpecRef("snap.sig.timestamp")
    @DisplayName("Timestamp outside the skew returns 4013000")
    void skew() {
        TestPartner partner = signupWithKey(false, 300);
        SnapTestClient client = snapClient(partner);
        String token = client.obtainToken();
        String old = SnapTimestamp.format(Instant.now().minusSeconds(600));
        Map<String, String> headers = client.serviceHeaders("POST", PATH, token, BODY);
        headers.put("X-TIMESTAMP", old);
        headers.put("X-SIGNATURE", SnapSignature.hmac(partner.clientSecret(),
                SnapSignature.symmetricStringToSign("POST", PATH, token, BODY, old)));
        assertCode(client.call("POST", PATH, headers, BODY), "4013000", "Unauthorized. X-TIMESTAMP outside allowed skew");
    }

    @Test
    @SpecRef("snap.sig.symmetric")
    @SpecRef("sim.diagnostic-mode")
    @DisplayName("HMAC over the raw (not minified) body fails with 4013000 and a diagnostic naming the mistake")
    void hmacDiagnostic() {
        TestPartner partner = signupWithKey(true, 300);
        SnapTestClient client = snapClient(partner);
        String token = client.obtainToken();
        Map<String, String> headers = client.serviceHeaders("POST", PATH, token, BODY);
        String timestamp = headers.get("X-TIMESTAMP");
        String wrong = "POST:" + PATH + ":" + token + ":" + SnapSignature.sha256Hex(BODY) + ":" + timestamp;
        headers.put("X-SIGNATURE", SnapSignature.hmac(partner.clientSecret(), wrong));

        JsonNode body = assertCode(client.call("POST", PATH, headers, BODY), "4013000", "Unauthorized. Signature");
        JsonNode diagnostic = body.at("/additionalInfo/diagnostic");
        assertThat(diagnostic.get("expectedStringToSign").asString())
                .isEqualTo(SnapSignature.symmetricStringToSign("POST", PATH, token, BODY, timestamp));
        assertThat(diagnostic.get("minifiedBody").asString())
                .isEqualTo("{\"partnerServiceId\":\"   10001\",\"virtualAccountNo\":\"   100011\"}");
        assertThat(diagnostic.get("rawBodySha256").asString()).isEqualTo(SnapSignature.sha256Hex(BODY));
        assertThat(diagnostic.get("hint").asString()).contains("did not minify");
        assertThat(checklistDone(partner, "SIGNED_CALL")).isNull();
    }

    @Test
    @SpecRef("snap.external-id")
    @DisplayName("A signed call stamps the checklist; reusing X-EXTERNAL-ID the same day returns 4093000")
    void externalIdUnique() {
        TestPartner partner = signupWithKey(false, 300);
        SnapTestClient client = snapClient(partner);
        String token = client.obtainToken();
        Map<String, String> headers = client.serviceHeaders("POST", PATH, token, BODY);
        APIResponse first = client.call("POST", PATH, headers, BODY);
        assertThat(first.status()).isNotIn(401, 409);
        assertThat(checklistDone(partner, "SIGNED_CALL")).isNotNull();

        Map<String, String> again = client.serviceHeaders("POST", PATH, token, BODY);
        again.put("X-EXTERNAL-ID", headers.get("X-EXTERNAL-ID"));
        again.put("X-TIMESTAMP", headers.get("X-TIMESTAMP"));
        again.put("X-SIGNATURE", headers.get("X-SIGNATURE"));
        assertCode(client.call("POST", PATH, again, BODY), "4093000", "Conflict");
    }

    @Test
    @SpecRef("sim.exchange-log")
    @DisplayName("Inbound calls are logged with headers, body and string-to-sign, also when rejected")
    void exchangeLogged() {
        TestPartner partner = signupWithKey(true, 300);
        SnapTestClient client = snapClient(partner);
        String token = client.obtainToken();
        Map<String, String> headers = client.serviceHeaders("POST", PATH, token, BODY);
        headers.put("X-SIGNATURE", "AAAA");
        client.call("POST", PATH, headers, BODY);

        JsonNode logs = SnapTestClient.json(api.get("/portal/api/exchanges?limit=10", basicAuth(partner)));
        JsonNode rejected = logs.get(0);
        assertThat(rejected.get("direction").asString()).isEqualTo("INBOUND");
        assertThat(rejected.get("url").asString()).isEqualTo(PATH);
        assertThat(rejected.get("responseStatus").asInt()).isEqualTo(401);
        assertThat(rejected.get("requestBody").asString()).isEqualTo(BODY);
        assertThat(rejected.get("stringToSign").asString())
                .isEqualTo(SnapSignature.symmetricStringToSign("POST", PATH, token, BODY, headers.get("X-TIMESTAMP")));
        assertThat(rejected.get("responseBody").asString()).contains("4013000");
        assertThat(logs.get(1).get("url").asString()).isEqualTo("/snap/v1.0/access-token/b2b");
    }
}
