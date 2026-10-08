package com.artivisi.snapsimulator.controller.snap;

import com.artivisi.snapsimulator.SpecRef;
import com.artivisi.snapsimulator.entity.AccessToken;
import com.artivisi.snapsimulator.entity.Partner;
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

import java.time.Clock;

@RestController
public class AccessTokenController {

    private static final SnapService SVC = SnapService.ACCESS_TOKEN_B2B;

    private final TokenService tokens;
    private final JsonMapper json;
    private final Clock clock;

    public AccessTokenController(TokenService tokens, JsonMapper json, Clock clock) {
        this.tokens = tokens;
        this.json = json;
        this.clock = clock;
    }

    public record TokenResponse(String accessToken, String tokenType, String expiresIn) {
    }

    /** Success body has no responseCode (A12); X-CLIENT-KEY echoed (A13). */
    @SpecRef("bri.oauth.token-b2b")
    @SpecRef("bri.oauth.token-b2b#request.grantType")
    @SpecRef("bri.oauth.token-b2b#response.accessToken")
    @SpecRef("bri.oauth.token-b2b#response.tokenType")
    @SpecRef("bri.oauth.token-b2b#response.expiresIn")
    @PostMapping("/snap/v1.0/access-token/b2b")
    public ResponseEntity<TokenResponse> token(@RequestAttribute(SnapInboundFilter.PARTNER) Partner partner,
            @RequestBody(required = false) String body) {
        JsonNode request;
        try {
            request = json.readTree(body == null ? "" : body);
        } catch (JacksonException e) {
            throw new SnapException(SVC.badRequest());
        }
        if (request == null || !request.isObject()) {
            throw new SnapException(SVC.badRequest());
        }
        JsonNode grantType = request.get("grantType");
        if (grantType == null || grantType.isNull() || grantType.asString().isBlank()) {
            throw new SnapException(SVC.invalidMandatoryField("grantType"));
        }
        if (!"client_credentials".equals(grantType.asString())) {
            throw new SnapException(SVC.invalidFieldFormat("grantType"));
        }
        AccessToken token = tokens.issue(partner, clock.instant());
        return ResponseEntity.ok()
                .header("X-CLIENT-KEY", partner.getClientId())
                .body(new TokenResponse(token.getToken(), "BearerToken", String.valueOf(partner.getTokenTtlSeconds())));
    }
}
