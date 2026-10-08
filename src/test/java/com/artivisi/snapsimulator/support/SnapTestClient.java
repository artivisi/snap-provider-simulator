package com.artivisi.snapsimulator.support;

import com.artivisi.snapsimulator.snap.SnapSignature;
import com.artivisi.snapsimulator.snap.SnapTimestamp;
import com.artivisi.snapsimulator.util.Randoms;
import com.microsoft.playwright.APIRequestContext;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.options.RequestOptions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.security.PrivateKey;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** A partner app's SNAP client, signing with the snap package; headers can be altered per call. */
public class SnapTestClient {

    public static final JsonMapper JSON = JsonMapper.builder().build();

    private final APIRequestContext api;
    private final String clientId;
    private final PrivateKey privateKey;
    private final String clientSecret;

    public SnapTestClient(APIRequestContext api, String clientId, PrivateKey privateKey, String clientSecret) {
        this.api = api;
        this.clientId = clientId;
        this.privateKey = privateKey;
        this.clientSecret = clientSecret;
    }

    public Map<String, String> tokenHeaders(String timestamp) {
        Map<String, String> h = new LinkedHashMap<>();
        h.put("Content-Type", "application/json");
        h.put("X-CLIENT-KEY", clientId);
        h.put("X-TIMESTAMP", timestamp);
        h.put("X-SIGNATURE", SnapSignature.signRsa(privateKey, SnapSignature.asymmetricStringToSign(clientId, timestamp)));
        return h;
    }

    public APIResponse token(Map<String, String> headers, String body) {
        RequestOptions options = RequestOptions.create().setData(body);
        headers.forEach(options::setHeader);
        return api.post("/snap/v1.0/access-token/b2b", options);
    }

    public String obtainToken() {
        APIResponse response = token(tokenHeaders(now()), "{\"grantType\":\"client_credentials\"}");
        if (response.status() != 200) {
            throw new IllegalStateException("token request failed: " + response.status() + " " + response.text());
        }
        return JSON.readTree(response.text()).get("accessToken").asString();
    }

    public Map<String, String> serviceHeaders(String method, String path, String accessToken, String body) {
        String timestamp = now();
        Map<String, String> h = new LinkedHashMap<>();
        h.put("Content-Type", "application/json");
        h.put("Authorization", "Bearer " + accessToken);
        h.put("X-TIMESTAMP", timestamp);
        h.put("X-SIGNATURE", SnapSignature.hmac(clientSecret,
                SnapSignature.symmetricStringToSign(method, path, accessToken, body, timestamp)));
        h.put("X-PARTNER-ID", clientId);
        h.put("CHANNEL-ID", "95221");
        h.put("X-EXTERNAL-ID", Randoms.digits(20));
        return h;
    }

    public APIResponse call(String method, String path, Map<String, String> headers, String body) {
        RequestOptions options = RequestOptions.create().setMethod(method).setData(body);
        headers.forEach(options::setHeader);
        return api.fetch(path, options);
    }

    /** Signs and sends with a fresh token-independent set of headers. */
    public APIResponse call(String method, String path, String accessToken, String body) {
        return call(method, path, serviceHeaders(method, path, accessToken, body), body);
    }

    public String clientSecret() {
        return clientSecret;
    }

    public static String now() {
        return SnapTimestamp.format(Instant.now());
    }

    public static JsonNode json(APIResponse response) {
        return JSON.readTree(response.text());
    }
}
