package com.artivisi.snapsimulator.image;

import com.artivisi.snapsimulator.SpecRef;
import com.artivisi.snapsimulator.snap.PemKeys;
import com.artivisi.snapsimulator.snap.SnapSignature;
import com.artivisi.snapsimulator.snap.SnapTimestamp;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the built image (-Dimage.name, required) against PostgreSQL, as a user
 * would with compose.yml. Run with: ./mvnw -P image-test test -Dimage.name=...
 */
@Tag("image")
class DockerImageTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final Network NETWORK = Network.newNetwork();
    private static PostgreSQLContainer postgres;
    private static GenericContainer<?> simulator;

    private static String image() {
        String image = System.getProperty("image.name");
        if (image == null || image.isBlank()) {
            throw new IllegalStateException("Set -Dimage.name to the image under test");
        }
        return image;
    }

    private static GenericContainer<?> simulator(Map<String, String> env) {
        return new GenericContainer<>(image())
                .withNetwork(NETWORK)
                .withCopyFileToContainer(MountableFile.forHostPath("src/test/resources/keys/bank-test-private.pem", 0644),
                        "/keys/bank-private.pem")
                .withEnv(env)
                .withExposedPorts(9090);
    }

    private static Map<String, String> env() {
        return Map.of(
                "SIMULATOR_DB_URL", "jdbc:postgresql://db:5432/simulator",
                "SIMULATOR_DB_USERNAME", "simulator",
                "SIMULATOR_DB_PASSWORD", "simulator",
                "SIMULATOR_TIMESTAMP_SKEW", "5m",
                "SIMULATOR_BANK_PRIVATE_KEY_PATH", "/keys/bank-private.pem",
                "SIMULATOR_OPERATOR_USERNAME", "operator",
                "SIMULATOR_OPERATOR_PASSWORD", "operator-password",
                "SIMULATOR_OUTBOUND_CONNECT_TIMEOUT", "2s",
                "SIMULATOR_OUTBOUND_READ_TIMEOUT", "5s");
    }

    @BeforeAll
    static void start() {
        postgres = new PostgreSQLContainer("postgres:18-alpine").withNetwork(NETWORK).withNetworkAliases("db")
                .withDatabaseName("simulator").withUsername("simulator").withPassword("simulator");
        postgres.start();
        simulator = simulator(env()).waitingFor(Wait.forHttp("/actuator/health/liveness").forStatusCode(200)
                .withStartupTimeout(Duration.ofMinutes(3)));
        simulator.start();
    }

    @AfterAll
    static void stop() {
        if (simulator != null) {
            simulator.stop();
        }
        postgres.stop();
        NETWORK.close();
    }

    private static String url(String path) {
        return "http://" + simulator.getHost() + ":" + simulator.getMappedPort(9090) + path;
    }

    private static HttpResponse<String> send(HttpRequest request) throws Exception {
        return HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    @Test
    @SpecRef("bri.oauth.token-b2b")
    @DisplayName("Image serves the portal API and the SNAP token endpoint")
    void signupAndToken() throws Exception {
        HttpResponse<String> signup = send(HttpRequest.newBuilder(URI.create(url("/portal/api/signup")))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"email\":\"image@example.test\",\"password\":\"password-1\","
                        + "\"tokenTtlSeconds\":300,\"diagnosticMode\":false}")).build());
        assertThat(signup.statusCode()).isEqualTo(201);
        String clientId = JSON.readTree(signup.body()).get("clientId").asString();
        String basic = "Basic " + Base64.getEncoder().encodeToString("image@example.test:password-1".getBytes(StandardCharsets.UTF_8));
        HttpResponse<String> key = send(HttpRequest.newBuilder(URI.create(url("/portal/api/key/generate")))
                .header("Authorization", basic).POST(HttpRequest.BodyPublishers.noBody()).build());
        assertThat(key.statusCode()).isEqualTo(200);
        var privateKey = PemKeys.parsePrivateKey(JSON.readTree(key.body()).get("privateKeyPem").asString());

        String timestamp = SnapTimestamp.format(Instant.now());
        HttpResponse<String> token = send(HttpRequest.newBuilder(URI.create(url("/snap/v1.0/access-token/b2b")))
                .header("Content-Type", "application/json")
                .header("X-CLIENT-KEY", clientId)
                .header("X-TIMESTAMP", timestamp)
                .header("X-SIGNATURE", SnapSignature.signRsa(privateKey, SnapSignature.asymmetricStringToSign(clientId, timestamp)))
                .POST(HttpRequest.BodyPublishers.ofString("{\"grantType\":\"client_credentials\"}")).build());
        assertThat(token.statusCode()).as(token.body()).isEqualTo(200);
        JsonNode body = JSON.readTree(token.body());
        assertThat(body.get("tokenType").asString()).isEqualTo("BearerToken");

        HttpResponse<String> bankKey = send(HttpRequest.newBuilder(URI.create(url("/keys/bank-public.pem"))).GET().build());
        assertThat(bankKey.body()).startsWith("-----BEGIN PUBLIC KEY-----");
    }

    @Test
    @DisplayName("Image runs as a non-root user in Asia/Jakarta")
    void runtime() throws Exception {
        assertThat(simulator.execInContainer("id", "-un").getStdout().trim()).isEqualTo("app");
        assertThat(simulator.execInContainer("date", "+%Z").getStdout().trim()).isEqualTo("WIB");
    }

    @Test
    @DisplayName("Missing environment variables stop startup with their names")
    void failsWithoutEnv() {
        Map<String, String> partial = new java.util.HashMap<>(env());
        partial.remove("SIMULATOR_OPERATOR_PASSWORD");
        partial.remove("SIMULATOR_TIMESTAMP_SKEW");
        try (GenericContainer<?> broken = simulator(partial)
                .waitingFor(Wait.forLogMessage(".*Required environment variables not set.*", 1)
                        .withStartupTimeout(Duration.ofMinutes(2)))) {
            broken.start();
            assertThat(broken.getLogs())
                    .contains("Required environment variables not set: SIMULATOR_TIMESTAMP_SKEW, SIMULATOR_OPERATOR_PASSWORD");
        }
    }
}
