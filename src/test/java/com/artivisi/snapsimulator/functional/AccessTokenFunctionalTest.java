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
import java.util.HexFormat;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpecRef("bri.oauth.token-b2b")
@SpecRef("snap.headers.token")
class AccessTokenFunctionalTest extends PlaywrightTestBase {

    private static final String GRANT = "{\"grantType\":\"client_credentials\"}";

    @Test
    @SpecRef("snap.sig.asymmetric-token")
    @SpecRef("bri.oauth.token-b2b#response.accessToken")
    @SpecRef("bri.oauth.token-b2b#response.tokenType")
    @SpecRef("bri.oauth.token-b2b#response.expiresIn")
    @DisplayName("Signed token request gets a BearerToken with the partner's TTL and stamps the checklist")
    void issuesToken() {
        TestPartner partner = signupWithKey(false, 120);
        assertThat(checklistDone(partner, "TOKEN_OBTAINED")).isNull();

        APIResponse response = snapClient(partner).token(snapClient(partner).tokenHeaders(SnapTestClient.now()), GRANT);

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.headers()).containsEntry("x-client-key", partner.clientId());
        assertThat(SnapTimestamp.parse(response.headers().get("x-timestamp"))).isNotNull();
        JsonNode body = SnapTestClient.json(response);
        assertThat(body.get("accessToken").asString()).hasSize(64);
        assertThat(body.get("tokenType").asString()).isEqualTo("BearerToken");
        assertThat(body.get("expiresIn").asString()).isEqualTo("120");
        assertThat(body.has("responseCode")).isFalse();
        assertThat(checklistDone(partner, "TOKEN_OBTAINED")).isNotNull();
    }

    @Test
    @DisplayName("Missing mandatory header returns 4007302 naming it; non-JSON Content-Type returns 4007301")
    void missingHeader() {
        TestPartner partner = signupWithKey(false, 120);
        SnapTestClient client = snapClient(partner);
        for (String header : new String[] {"X-CLIENT-KEY", "X-TIMESTAMP", "X-SIGNATURE"}) {
            Map<String, String> headers = client.tokenHeaders(SnapTestClient.now());
            headers.remove(header);
            APIResponse response = client.token(headers, GRANT);
            assertThat(response.status()).as(header).isEqualTo(400);
            assertCode(response, "4007302", "Invalid Mandatory Field " + header);
        }
        Map<String, String> plain = client.tokenHeaders(SnapTestClient.now());
        plain.put("Content-Type", "text/plain");
        assertCode(client.token(plain, GRANT), "4007301", "Invalid Field Format Content-Type");
    }

    @Test
    @SpecRef("snap.sig.timestamp")
    @DisplayName("Timestamp without offset returns 4007301; outside the skew returns 4017300")
    void timestampChecks() {
        TestPartner partner = signupWithKey(true, 120);
        SnapTestClient client = snapClient(partner);
        APIResponse noOffset = client.token(client.tokenHeaders("2026-10-08T10:00:00.000"), GRANT);
        assertThat(noOffset.status()).isEqualTo(400);
        assertCode(noOffset, "4007301", "Invalid Field Format X-TIMESTAMP");

        String old = SnapTimestamp.format(Instant.now().minusSeconds(3600));
        APIResponse skewed = client.token(client.tokenHeaders(old), GRANT);
        assertThat(skewed.status()).isEqualTo(401);
        JsonNode body = assertCode(skewed, "4017300", "Unauthorized. X-TIMESTAMP outside allowed skew");
        assertThat(body.at("/additionalInfo/diagnostic/receivedTimestamp").asString()).isEqualTo(old);
        assertThat(body.at("/additionalInfo/diagnostic/allowedSkew").asString()).isEqualTo("PT5M");
    }

    @Test
    @DisplayName("Unknown client id returns 4017300")
    void unknownClient() {
        TestPartner partner = signupWithKey(true, 120);
        SnapTestClient client = snapClient(partner);
        Map<String, String> headers = client.tokenHeaders(SnapTestClient.now());
        headers.put("X-CLIENT-KEY", "nobody");
        APIResponse response = client.token(headers, GRANT);
        assertThat(response.status()).isEqualTo(401);
        JsonNode body = assertCode(response, "4017300", "Unauthorized. Unknown client");
        assertThat(body.has("additionalInfo")).isFalse();
    }

    @Test
    @SpecRef("sim.diagnostic-mode")
    @DisplayName("Bad signature: diagnostic mode returns the string-to-sign, never a signature; off returns nothing")
    void badSignatureDiagnostic() {
        TestPartner withDiagnostic = signupWithKey(true, 120);
        SnapTestClient client = snapClient(withDiagnostic);
        String timestamp = SnapTestClient.now();
        Map<String, String> headers = client.tokenHeaders(timestamp);
        String good = headers.get("X-SIGNATURE");
        headers.put("X-SIGNATURE", SnapSignature.signRsa(withDiagnostic.privateKey(), "wrong|" + timestamp));
        APIResponse response = client.token(headers, GRANT);
        assertThat(response.status()).isEqualTo(401);
        JsonNode body = assertCode(response, "4017300", "Unauthorized. Signature");
        assertThat(body.at("/additionalInfo/diagnostic/expectedStringToSign").asString())
                .isEqualTo(withDiagnostic.clientId() + "|" + timestamp);
        assertThat(response.text()).doesNotContain(good);

        headers.put("X-SIGNATURE", HexFormat.of().formatHex(java.util.Base64.getDecoder().decode(good)));
        assertThat(SnapTestClient.json(client.token(headers, GRANT)).at("/additionalInfo/diagnostic/hint").asString())
                .contains("looks hex");

        TestPartner withoutDiagnostic = signupWithKey(false, 120);
        SnapTestClient quiet = snapClient(withoutDiagnostic);
        Map<String, String> quietHeaders = quiet.tokenHeaders(SnapTestClient.now());
        quietHeaders.put("X-SIGNATURE", "AAAA");
        JsonNode quietBody = assertCode(quiet.token(quietHeaders, GRANT), "4017300", "Unauthorized. Signature");
        assertThat(quietBody.has("additionalInfo")).isFalse();
    }

    @Test
    @SpecRef("bri.oauth.token-b2b#request.grantType")
    @DisplayName("grantType missing returns 4007302, other value 4007301, non-JSON body 4007300")
    void grantType() {
        TestPartner partner = signupWithKey(false, 120);
        SnapTestClient client = snapClient(partner);
        assertCode(client.token(client.tokenHeaders(SnapTestClient.now()), "{}"), "4007302", "Invalid Mandatory Field grantType");
        assertCode(client.token(client.tokenHeaders(SnapTestClient.now()), "{\"grantType\":\"password\"}"),
                "4007301", "Invalid Field Format grantType");
        assertCode(client.token(client.tokenHeaders(SnapTestClient.now()), "not json"), "4007300", "Bad Request");
    }

    @Test
    @DisplayName("Partner without a registered key gets 4017300 with a hint")
    void noKey() {
        APIResponse signup = api.post("/portal/api/signup", com.microsoft.playwright.options.RequestOptions.create()
                .setHeader("Content-Type", "application/json")
                .setData("{\"email\":\"nokey-" + System.nanoTime() + "@example.test\",\"password\":\"password-123\","
                        + "\"tokenTtlSeconds\":60,\"diagnosticMode\":true}"));
        String clientId = SnapTestClient.json(signup).get("clientId").asString();
        SnapTestClient client = new SnapTestClient(api, clientId,
                com.artivisi.snapsimulator.snap.PemKeys.generateRsa().getPrivate(), "unused");
        JsonNode body = assertCode(client.token(client.tokenHeaders(SnapTestClient.now()), GRANT),
                "4017300", "Unauthorized. No public key registered");
        assertThat(body.at("/additionalInfo/diagnostic/hint").asString()).contains("no public key registered");
    }

    static JsonNode assertCode(APIResponse response, String code, String message) {
        JsonNode body = SnapTestClient.json(response);
        assertThat(body.get("responseCode").asString()).isEqualTo(code);
        assertThat(body.get("responseMessage").asString()).isEqualTo(message);
        assertThat(response.status()).isEqualTo(Integer.parseInt(code.substring(0, 3)));
        return body;
    }
}
