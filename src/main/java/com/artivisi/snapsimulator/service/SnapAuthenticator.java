package com.artivisi.snapsimulator.service;

import com.artivisi.snapsimulator.SpecRef;
import com.artivisi.snapsimulator.config.SimulatorProperties;
import com.artivisi.snapsimulator.entity.Partner;
import com.artivisi.snapsimulator.enums.ChecklistItem;
import com.artivisi.snapsimulator.exception.SnapException;
import com.artivisi.snapsimulator.repository.PartnerRepository;
import com.artivisi.snapsimulator.snap.PemKeys;
import com.artivisi.snapsimulator.snap.SignatureDiagnostics;
import com.artivisi.snapsimulator.snap.SnapService;
import com.artivisi.snapsimulator.snap.SnapSignature;
import com.artivisi.snapsimulator.snap.SnapTimestamp;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

import java.security.PublicKey;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The inbound check chain. Request attributes {@link #PARTNER_ID} and
 * {@link #STRING_TO_SIGN} are set as soon as they are known, so the exchange
 * log can record them for failed calls too.
 */
@Service
public class SnapAuthenticator {

    public static final String PARTNER_ID = "snap.partnerId";
    public static final String STRING_TO_SIGN = "snap.stringToSign";

    private static final Pattern PARTNER_ID_FORMAT = Pattern.compile("[A-Za-z0-9]{1,36}");
    private static final Pattern CHANNEL_ID_FORMAT = Pattern.compile("\\d{5}");
    private static final Pattern EXTERNAL_ID_FORMAT = Pattern.compile("\\d{1,36}");

    private final PartnerRepository partners;
    private final TokenService tokens;
    private final ExternalIdService externalIds;
    private final ChecklistService checklist;
    private final SimulatorProperties properties;

    public SnapAuthenticator(PartnerRepository partners, TokenService tokens, ExternalIdService externalIds,
            ChecklistService checklist, SimulatorProperties properties) {
        this.partners = partners;
        this.tokens = tokens;
        this.externalIds = externalIds;
        this.checklist = checklist;
        this.properties = properties;
    }

    /** Asymmetric check for the B2B token request. */
    @SpecRef("snap.headers.token")
    @SpecRef("snap.sig.asymmetric-token")
    public Partner authenticateToken(HttpServletRequest request, Instant now) {
        SnapService svc = SnapService.ACCESS_TOKEN_B2B;
        requireJson(request, svc);
        String clientKey = mandatory(request, "X-CLIENT-KEY", svc);
        String timestamp = mandatory(request, "X-TIMESTAMP", svc);
        String signature = mandatory(request, "X-SIGNATURE", svc);
        OffsetDateTime parsed = parseTimestamp(timestamp, svc);

        Partner partner = partners.findByClientId(clientKey)
                .orElseThrow(() -> new SnapException(svc.unauthorized("Unknown client")));
        request.setAttribute(PARTNER_ID, partner.getId());
        request.setAttribute(STRING_TO_SIGN, SnapSignature.asymmetricStringToSign(clientKey, timestamp));
        requireEnabled(partner, svc);
        checkSkew(partner, parsed, timestamp, now, svc);

        PublicKey key = partner.getPublicKeyPem() == null ? null : PemKeys.parsePublicKey(partner.getPublicKeyPem());
        if (key == null || !SnapSignature.verifyRsa(key, SnapSignature.asymmetricStringToSign(clientKey, timestamp), signature)) {
            throw new SnapException(svc.unauthorized(key == null ? "No public key registered" : "Signature"),
                    partner.isDiagnosticMode() ? SignatureDiagnostics.asymmetric(clientKey, timestamp, signature, key) : null);
        }
        return partner;
    }

    /** Symmetric check chain for service calls: token, headers, timestamp, HMAC, X-EXTERNAL-ID. */
    @SpecRef("snap.headers.service")
    @SpecRef("snap.sig.symmetric")
    @SpecRef("snap.sig.timestamp")
    @SpecRef("snap.external-id")
    public Partner authenticateService(HttpServletRequest request, String body, SnapService svc, Instant now) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new SnapException(svc.invalidToken());
        }
        String accessToken = authorization.substring("Bearer ".length());
        Partner partner = tokens.resolve(accessToken, now).orElseThrow(() -> new SnapException(svc.invalidToken()));
        request.setAttribute(PARTNER_ID, partner.getId());
        requireEnabled(partner, svc);

        requireJson(request, svc);
        String timestamp = mandatory(request, "X-TIMESTAMP", svc);
        String signature = mandatory(request, "X-SIGNATURE", svc);
        String partnerIdHeader = mandatory(request, "X-PARTNER-ID", svc);
        String channelId = mandatory(request, "CHANNEL-ID", svc);
        String externalId = mandatory(request, "X-EXTERNAL-ID", svc);
        requireFormat(partnerIdHeader, PARTNER_ID_FORMAT, "X-PARTNER-ID", svc);
        requireFormat(channelId, CHANNEL_ID_FORMAT, "CHANNEL-ID", svc);
        requireFormat(externalId, EXTERNAL_ID_FORMAT, "X-EXTERNAL-ID", svc);
        OffsetDateTime parsed = parseTimestamp(timestamp, svc);
        checkSkew(partner, parsed, timestamp, now, svc);

        String path = request.getRequestURI();
        String stringToSign;
        try {
            stringToSign = SnapSignature.symmetricStringToSign(request.getMethod(), path, accessToken, body, timestamp);
        } catch (IllegalArgumentException e) {
            throw new SnapException(svc.badRequest());
        }
        request.setAttribute(STRING_TO_SIGN, stringToSign);
        if (!SnapSignature.verifyHmac(partner.getClientSecret(), stringToSign, signature)) {
            throw new SnapException(svc.unauthorized("Signature"), partner.isDiagnosticMode()
                    ? SignatureDiagnostics.symmetric(request.getMethod(), path, request.getQueryString(), accessToken,
                            body, timestamp, partner.getClientSecret(), signature)
                    : null);
        }
        checklist.stamp(partner.getId(), ChecklistItem.SIGNED_CALL, now);

        if (!externalIds.register(partner.getId(), externalId, now)) {
            throw new SnapException(svc.conflict());
        }
        return partner;
    }

    private static void requireJson(HttpServletRequest request, SnapService svc) {
        String contentType = mandatory(request, "Content-Type", svc);
        if (!contentType.toLowerCase(java.util.Locale.ROOT).startsWith("application/json")) {
            throw new SnapException(svc.invalidFieldFormat("Content-Type"));
        }
    }

    private static String mandatory(HttpServletRequest request, String header, SnapService svc) {
        String value = request.getHeader(header);
        if (value == null || value.isBlank()) {
            throw new SnapException(svc.invalidMandatoryField(header));
        }
        return value;
    }

    private static void requireFormat(String value, Pattern format, String header, SnapService svc) {
        if (!format.matcher(value).matches()) {
            throw new SnapException(svc.invalidFieldFormat(header));
        }
    }

    private static OffsetDateTime parseTimestamp(String timestamp, SnapService svc) {
        try {
            return SnapTimestamp.parse(timestamp);
        } catch (DateTimeParseException e) {
            throw new SnapException(svc.invalidFieldFormat("X-TIMESTAMP"));
        }
    }

    private static void requireEnabled(Partner partner, SnapService svc) {
        if (!partner.isEnabled()) {
            throw new SnapException(svc.unauthorized("Client disabled"));
        }
    }

    private void checkSkew(Partner partner, OffsetDateTime parsed, String timestamp, Instant now, SnapService svc) {
        if (!SnapTimestamp.withinSkew(parsed, now, properties.timestampSkew())) {
            Map<String, String> diagnostic = null;
            if (partner.isDiagnosticMode()) {
                diagnostic = new LinkedHashMap<>();
                diagnostic.put("receivedTimestamp", timestamp);
                diagnostic.put("serverTime", SnapTimestamp.format(now));
                diagnostic.put("allowedSkew", properties.timestampSkew().toString());
            }
            throw new SnapException(svc.unauthorized("X-TIMESTAMP outside allowed skew"), diagnostic);
        }
    }
}
