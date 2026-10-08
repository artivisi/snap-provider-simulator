package com.artivisi.snapsimulator.support;

import com.artivisi.snapsimulator.snap.SnapSignature;
import com.artivisi.snapsimulator.snap.SnapTimestamp;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * A partner app answering the bank's calls under {base}/v1.0. It verifies the
 * bank's RSA token signature and HMAC service signatures like a real partner
 * would, and rejects a reused X-EXTERNAL-ID on payment with 4092500.
 */
public class FakePartnerApp implements AutoCloseable {

    public static final String BASE_PATH = "/partner";
    public static final String TOKEN = "partner-issued-token";

    public record Received(String path, Map<String, String> headers, JsonNode body, boolean signatureValid) {
    }

    /** status and body the app answers with; body may be null. */
    public record Answer(int status, String body) {
    }

    private final HttpServer server;
    private final String bankClientId;
    private final String bankClientSecret;
    private final PublicKey bankPublicKey;
    private final List<Received> received = new CopyOnWriteArrayList<>();
    private final Set<String> paymentExternalIds = ConcurrentHashMap.newKeySet();
    private volatile Function<JsonNode, Answer> inquiryAnswer;
    private volatile Function<JsonNode, Answer> paymentAnswer;

    public FakePartnerApp(String bankClientId, String bankClientSecret, PublicKey bankPublicKey) throws IOException {
        this.bankClientId = bankClientId;
        this.bankClientSecret = bankClientSecret;
        this.bankPublicKey = bankPublicKey;
        this.inquiryAnswer = body -> new Answer(200, inquirySuccess(body));
        this.paymentAnswer = body -> new Answer(200, paymentReply(body, "2002500", "00"));
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(BASE_PATH + "/v1.0/access-token/b2b", this::token);
        server.createContext(BASE_PATH + "/v1.0/transfer-va/inquiry", ex -> service(ex, true));
        server.createContext(BASE_PATH + "/v1.0/transfer-va/payment", ex -> service(ex, false));
        server.start();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + BASE_PATH;
    }

    public List<Received> received() {
        return received;
    }

    public List<Received> received(String suffix) {
        return received.stream().filter(r -> r.path().endsWith(suffix)).toList();
    }

    public void answerInquiry(Function<JsonNode, Answer> answer) {
        this.inquiryAnswer = answer;
    }

    public void answerPayment(Function<JsonNode, Answer> answer) {
        this.paymentAnswer = answer;
    }

    public static String inquirySuccess(JsonNode request) {
        return """
                {"responseCode":"2002400","responseMessage":"Successful","virtualAccountData":{
                "partnerServiceId":"%s","customerNo":"%s","virtualAccountNo":"%s","virtualAccountName":"Siti Aminah",
                "inquiryRequestId":"%s","totalAmount":{"value":"250000.00","currency":"IDR"},"inquiryStatus":"00"}}"""
                .formatted(request.get("partnerServiceId").asString(), request.get("customerNo").asString(),
                        request.get("virtualAccountNo").asString(), request.get("inquiryRequestId").asString());
    }

    public static String paymentReply(JsonNode request, String responseCode, String flag) {
        return """
                {"responseCode":"%s","responseMessage":"reply","virtualAccountData":{
                "partnerServiceId":"%s","customerNo":"%s","virtualAccountNo":"%s","virtualAccountName":"x",
                "paymentRequestId":"%s","paymentFlagStatus":"%s"}}"""
                .formatted(responseCode, request.get("partnerServiceId").asString(), request.get("customerNo").asString(),
                        request.get("virtualAccountNo").asString(), request.get("paymentRequestId").asString(), flag);
    }

    private void token(HttpExchange exchange) throws IOException {
        Map<String, String> headers = headers(exchange);
        String body = read(exchange);
        boolean valid = bankClientId.equals(headers.get("x-client-key")) && SnapSignature.verifyRsa(bankPublicKey,
                SnapSignature.asymmetricStringToSign(headers.get("x-client-key"), headers.get("x-timestamp")),
                headers.get("x-signature"));
        received.add(new Received(exchange.getRequestURI().getPath(), headers, SnapTestClient.JSON.readTree(body), valid));
        if (!valid) {
            respond(exchange, 401, "{\"responseCode\":\"4017300\",\"responseMessage\":\"Unauthorized. Signature\"}");
            return;
        }
        respond(exchange, 200, "{\"responseCode\":\"2007300\",\"responseMessage\":\"Successful\",\"accessToken\":\""
                + TOKEN + "\",\"tokenType\":\"Bearer\",\"expiresIn\":\"900\"}");
    }

    private void service(HttpExchange exchange, boolean inquiry) throws IOException {
        Map<String, String> headers = headers(exchange);
        String body = read(exchange);
        String path = exchange.getRequestURI().getPath();
        boolean valid = ("Bearer " + TOKEN).equals(headers.get("authorization"))
                && SnapSignature.verifyHmac(bankClientSecret, SnapSignature.symmetricStringToSign("POST", path, TOKEN,
                        body, headers.get("x-timestamp")), headers.get("x-signature"));
        JsonNode json = SnapTestClient.JSON.readTree(body);
        received.add(new Received(path, headers, json, valid));
        String service = inquiry ? "24" : "25";
        if (!valid) {
            respond(exchange, 401, "{\"responseCode\":\"401" + service + "00\",\"responseMessage\":\"Unauthorized. Signature\"}");
            return;
        }
        if (!inquiry && !paymentExternalIds.add(headers.get("x-external-id"))) {
            respond(exchange, 409, "{\"responseCode\":\"4092500\",\"responseMessage\":\"Conflict\"}");
            return;
        }
        Answer answer = (inquiry ? inquiryAnswer : paymentAnswer).apply(json);
        respond(exchange, answer.status(), answer.body());
    }

    private static Map<String, String> headers(HttpExchange exchange) {
        Map<String, String> headers = new ConcurrentHashMap<>();
        exchange.getRequestHeaders().forEach((k, v) -> headers.put(k.toLowerCase(java.util.Locale.ROOT), v.getFirst()));
        return headers;
    }

    private static String read(HttpExchange exchange) throws IOException {
        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set("X-TIMESTAMP", SnapTimestamp.format(Instant.now()));
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            exchange.getResponseBody().write(bytes);
        }
        exchange.close();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
