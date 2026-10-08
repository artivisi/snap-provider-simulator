package com.artivisi.snapsimulator.controller.snap;

import com.artivisi.snapsimulator.SpecRef;
import com.artivisi.snapsimulator.bank.BankProfile;
import com.artivisi.snapsimulator.bank.BankProfiles;
import com.artivisi.snapsimulator.entity.AccessToken;
import com.artivisi.snapsimulator.entity.BankConnection;
import com.artivisi.snapsimulator.exception.SnapException;
import com.artivisi.snapsimulator.service.TokenService;
import com.artivisi.snapsimulator.snap.SnapService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.Clock;

@RestController
public class AccessTokenController {

    private final TokenService tokens;
    private final BankProfiles profiles;
    private final JsonMapper json;
    private final Clock clock;

    public AccessTokenController(TokenService tokens, BankProfiles profiles, JsonMapper json, Clock clock) {
        this.tokens = tokens;
        this.profiles = profiles;
        this.json = json;
        this.clock = clock;
    }

    /** BRI: no responseCode on success (A12). X-CLIENT-KEY echoed (A13). */
    @SpecRef("bri.oauth.token-b2b")
    @SpecRef("bri.oauth.token-b2b#request.grantType")
    @SpecRef("bri.oauth.token-b2b#response.accessToken")
    @SpecRef("bri.oauth.token-b2b#response.expiresIn")
    @PostMapping("/snap/v1.0/access-token/b2b")
    public ResponseEntity<ObjectNode> bri(@RequestAttribute(SnapInboundFilter.CONNECTION) BankConnection connection,
            @RequestBody(required = false) String body) {
        return token(connection, body, SnapService.BRI_ACCESS_TOKEN);
    }

    /** BCA: responseCode 2007300, tokenType bearer (A38). */
    @SpecRef("bca.oauth.token-b2b")
    @SpecRef("bca.oauth.token-b2b#response.accessToken")
    @SpecRef("bca.oauth.token-b2b#response.expiresIn")
    @PostMapping("/openapi/v1.0/access-token/b2b")
    public ResponseEntity<ObjectNode> bca(@RequestAttribute(SnapInboundFilter.CONNECTION) BankConnection connection,
            @RequestBody(required = false) String body) {
        return token(connection, body, SnapService.BCA_ACCESS_TOKEN);
    }

    private ResponseEntity<ObjectNode> token(BankConnection connection, String body, SnapService svc) {
        JsonNode request;
        try {
            request = json.readTree(body == null ? "" : body);
        } catch (JacksonException e) {
            throw new SnapException(svc.badRequest());
        }
        if (request == null || !request.isObject()) {
            throw new SnapException(svc.badRequest());
        }
        JsonNode grantType = request.get("grantType");
        if (grantType == null || grantType.isNull() || grantType.asString().isBlank()) {
            throw new SnapException(svc.invalidMandatoryField("grantType"));
        }
        if (!"client_credentials".equals(grantType.asString())) {
            throw new SnapException(svc.invalidFieldFormat("grantType"));
        }
        AccessToken token = tokens.issue(connection, clock.instant());
        BankProfile profile = profiles.of(svc.bank());
        return ResponseEntity.ok()
                .header("X-CLIENT-KEY", connection.getClientId())
                .body(profile.tokenResponse(token.getToken(), connection.getTokenTtlSeconds()));
    }
}
