package com.artivisi.snapsimulator.service;

import com.artivisi.snapsimulator.SpecRef;
import com.artivisi.snapsimulator.bank.BankProfile;
import com.artivisi.snapsimulator.bank.BankProfiles;
import com.artivisi.snapsimulator.bank.ChannelOption;
import com.artivisi.snapsimulator.config.BankKeys;
import com.artivisi.snapsimulator.dto.OutboundExchange;
import com.artivisi.snapsimulator.config.SimulatorProperties;
import com.artivisi.snapsimulator.entity.ExchangeLog;
import com.artivisi.snapsimulator.entity.BankConnection;
import com.artivisi.snapsimulator.enums.ChecklistItem;
import com.artivisi.snapsimulator.enums.Direction;
import com.artivisi.snapsimulator.exception.OutboundException;
import com.artivisi.snapsimulator.snap.SnapSignature;
import com.artivisi.snapsimulator.util.Randoms;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A bank calling the partner app: B2B token signed with that bank's key (A27,
 * A39), then HMAC-signed service calls under {base URL}/v1.0 (A26). Headers and
 * timestamps follow the connection's bank profile. Every call, including
 * failures, is written to the exchange log.
 */
@Service
public class PartnerClient {

    public static final String TOKEN_PATH = "/v1.0/access-token/b2b";
    public static final String INQUIRY_PATH = "/v1.0/transfer-va/inquiry";
    public static final String PAYMENT_PATH = "/v1.0/transfer-va/payment";

    private final HttpClient http;
    private final SimulatorProperties properties;
    private final BankKeys bankKeys;
    private final BankProfiles profiles;
    private final ExchangeLogService exchangeLog;
    private final ChecklistService checklist;
    private final JsonMapper json;
    private final Clock clock;
    /** Tokens the bank holds from partners: a cache, re-requested after expiry or restart. */
    private final Map<UUID, CachedToken> tokens = new ConcurrentHashMap<>();

    record CachedToken(String baseUrl, String clientId, String token, Instant expiresAt) {
    }

    public PartnerClient(SimulatorProperties properties, BankKeys bankKeys, BankProfiles profiles,
            ExchangeLogService exchangeLog, ChecklistService checklist, JsonMapper json, Clock clock) {
        this.properties = properties;
        this.bankKeys = bankKeys;
        this.profiles = profiles;
        this.exchangeLog = exchangeLog;
        this.checklist = checklist;
        this.json = json;
        this.clock = clock;
        this.http = HttpClient.newBuilder().connectTimeout(properties.outbound().connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    /** Requests a new token, ignoring the cache; used by "test connection". */
    @SpecRef("aspi.oauth.token-b2b-outbound")
    @SpecRef("aspi.oauth.token-b2b-outbound#request.grantType")
    @SpecRef("aspi.oauth.token-b2b-outbound#response.accessToken")
    @SpecRef("aspi.oauth.token-b2b-outbound#response.expiresIn")
    public OutboundExchange requestToken(BankConnection partner) {
        requireEndpoint(partner);
        tokens.remove(partner.getId());
        String timestamp = profiles.of(partner.getBank()).timestamp(clock.instant());
        String stringToSign = SnapSignature.asymmetricStringToSign(partner.getEndpointClientId(), timestamp);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("X-CLIENT-KEY", partner.getEndpointClientId());
        headers.put("X-TIMESTAMP", timestamp);
        headers.put("X-SIGNATURE", SnapSignature.signRsa(bankKeys.of(partner.getBank()).privateKey(), stringToSign));
        OutboundExchange exchange = send(partner, partner.getEndpointBaseUrl() + TOKEN_PATH, headers,
                "{\"grantType\":\"client_credentials\"}", stringToSign);
        if (exchange.status() == null || exchange.status() != 200 || exchange.json() == null
                || exchange.json().get("accessToken") == null || exchange.json().get("accessToken").asString().isBlank()) {
            return exchange;
        }
        long expiresIn = expiresIn(exchange.json().get("expiresIn"));
        tokens.put(partner.getId(), new CachedToken(partner.getEndpointBaseUrl(), partner.getEndpointClientId(),
                exchange.json().get("accessToken").asString(), clock.instant().plusSeconds(expiresIn)));
        checklist.stamp(partner.getId(), ChecklistItem.ENDPOINT_REACHABLE, clock.instant());
        return exchange;
    }

    /** A token from the cache, or a new one. */
    public String token(BankConnection partner) {
        CachedToken cached = tokens.get(partner.getId());
        if (cached != null && cached.expiresAt().isAfter(clock.instant())
                && cached.baseUrl().equals(partner.getEndpointBaseUrl())
                && cached.clientId().equals(partner.getEndpointClientId())) {
            return cached.token();
        }
        OutboundExchange exchange = requestToken(partner);
        CachedToken fresh = tokens.get(partner.getId());
        if (fresh == null) {
            throw new OutboundException("Partner token request failed: " + exchange.describe());
        }
        return fresh.token();
    }

    /**
     * HMAC-signed POST to the partner. X-PARTNER-ID and CHANNEL-ID follow the
     * bank profile (A28, A40); corruptSignature sends a wrong signature on purpose.
     */
    @SpecRef("snap.headers.service")
    @SpecRef("snap.sig.symmetric")
    @SpecRef("bca.headers.service")
    public OutboundExchange post(BankConnection partner, String path, String body, ChannelOption channel,
            String externalId, boolean corruptSignature) {
        requireEndpoint(partner);
        String token = token(partner);
        BankProfile profile = profiles.of(partner.getBank());
        String timestamp = profile.timestamp(clock.instant());
        String signedPath = URI.create(partner.getEndpointBaseUrl() + path).getRawPath();
        String stringToSign = SnapSignature.symmetricStringToSign("POST", signedPath, token, body, timestamp);
        String signature = SnapSignature.hmac(partner.getEndpointClientSecret(), stringToSign);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Authorization", "Bearer " + token);
        headers.put("X-TIMESTAMP", timestamp);
        headers.put("X-SIGNATURE", corruptSignature ? corrupt(signature) : signature);
        headers.put("X-PARTNER-ID", profile.outboundPartnerId(partner));
        headers.put("CHANNEL-ID", channel.channelIdHeader());
        headers.put("X-EXTERNAL-ID", externalId);
        OutboundExchange exchange = send(partner, partner.getEndpointBaseUrl() + path, headers, body, stringToSign);
        if (exchange.status() != null && exchange.status() == 401) {
            tokens.remove(partner.getId());
        }
        return exchange;
    }

    /** 32 random digits (A8). */
    public static String newExternalId() {
        return Randoms.digits(32);
    }

    private OutboundExchange send(BankConnection partner, String url, Map<String, String> headers, String body, String stringToSign) {
        Instant started = clock.instant();
        long startNanos = System.nanoTime();
        ExchangeLog entry = new ExchangeLog();
        entry.setCreatedAt(started);
        entry.setConnectionId(partner.getId());
        entry.setDirection(Direction.OUTBOUND);
        entry.setMethod("POST");
        entry.setUrl(url);
        entry.setRequestHeaders(json.writeValueAsString(headers));
        entry.setRequestBody(body);
        entry.setStringToSign(stringToSign);
        Integer status = null;
        String responseBody = null;
        String error = null;
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(properties.outbound().readTimeout())
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            headers.forEach(request::header);
            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            status = response.statusCode();
            responseBody = response.body();
            Map<String, String> responseHeaders = new LinkedHashMap<>();
            response.headers().map().forEach((k, v) -> responseHeaders.put(k, String.join(", ", v)));
            entry.setResponseHeaders(json.writeValueAsString(responseHeaders));
        } catch (HttpTimeoutException e) {
            error = "timeout after " + properties.outbound().readTimeout();
        } catch (IOException | IllegalArgumentException e) {
            error = e.getClass().getSimpleName() + ": " + e.getMessage();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            error = "interrupted";
        }
        entry.setResponseStatus(status);
        entry.setResponseBody(responseBody);
        entry.setError(error);
        entry.setDurationMs((System.nanoTime() - startNanos) / 1_000_000);
        exchangeLog.record(entry);
        return new OutboundExchange(entry.getId(), status, responseBody, parse(responseBody), error);
    }

    private JsonNode parse(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode node = json.readTree(body);
            return node != null && node.isObject() ? node : null;
        } catch (JacksonException e) {
            return null;
        }
    }

    private static long expiresIn(JsonNode value) {
        if (value == null || value.isNull()) {
            throw new OutboundException("Partner token response has no expiresIn");
        }
        try {
            return Long.parseLong(value.asString().trim());
        } catch (NumberFormatException e) {
            throw new OutboundException("Partner token response has a non-numeric expiresIn: " + value);
        }
    }

    private static void requireEndpoint(BankConnection partner) {
        if (!partner.hasEndpoint()) {
            throw new OutboundException("No partner endpoint registered: set it on the portal Endpoint page");
        }
    }

    private static String corrupt(String signature) {
        char first = signature.charAt(0);
        return (first == 'A' ? 'B' : 'A') + signature.substring(1);
    }
}
