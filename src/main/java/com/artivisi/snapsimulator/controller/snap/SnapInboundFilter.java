package com.artivisi.snapsimulator.controller.snap;

import com.artivisi.snapsimulator.SpecRef;
import com.artivisi.snapsimulator.entity.ExchangeLog;
import com.artivisi.snapsimulator.entity.Partner;
import com.artivisi.snapsimulator.enums.Direction;
import com.artivisi.snapsimulator.exception.SnapException;
import com.artivisi.snapsimulator.service.ExchangeLogService;
import com.artivisi.snapsimulator.service.SnapAuthenticator;
import com.artivisi.snapsimulator.snap.SnapService;
import com.artivisi.snapsimulator.snap.SnapTimestamp;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Runs the check chain before every inbound SNAP call and records the exchange.
 * Handlers read the authenticated partner from {@link #PARTNER}.
 */
@Component
public class SnapInboundFilter extends OncePerRequestFilter {

    public static final String PARTNER = "snap.partner";
    public static final String SERVICE = "snap.service";

    private final SnapAuthenticator authenticator;
    private final ExchangeLogService exchangeLog;
    private final JsonMapper json;
    private final Clock clock;

    public SnapInboundFilter(SnapAuthenticator authenticator, ExchangeLogService exchangeLog, JsonMapper json,
            Clock clock) {
        this.authenticator = authenticator;
        this.exchangeLog = exchangeLog;
        this.json = json;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return SnapService.match(request.getMethod(), request.getRequestURI()).isEmpty();
    }

    @Override
    @SpecRef("snap.headers.service")
    @SpecRef("sim.exchange-log")
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        SnapService svc = SnapService.match(request.getMethod(), request.getRequestURI()).orElseThrow();
        long started = System.nanoTime();
        Instant now = clock.instant();
        CachedBodyRequest wrapped = new CachedBodyRequest(request, request.getInputStream().readAllBytes());
        ContentCachingResponseWrapper captured = new ContentCachingResponseWrapper(response);
        wrapped.setAttribute(SERVICE, svc);
        captured.setHeader("X-TIMESTAMP", SnapTimestamp.format(now));
        String error = null;
        try {
            Partner partner = svc == SnapService.ACCESS_TOKEN_B2B
                    ? authenticator.authenticateToken(wrapped, now)
                    : authenticator.authenticateService(wrapped, wrapped.bodyText(), svc, now);
            wrapped.setAttribute(PARTNER, partner);
            chain.doFilter(wrapped, captured);
        } catch (SnapException e) {
            error = e.getMessage();
            captured.setStatus(e.responseCode().httpStatus());
            captured.setContentType(MediaType.APPLICATION_JSON_VALUE);
            captured.getOutputStream().write(json.writeValueAsBytes(SnapErrorBody.of(e)));
        } finally {
            record(wrapped, captured, now, started, error);
            captured.copyBodyToResponse();
        }
    }

    private void record(CachedBodyRequest request, ContentCachingResponseWrapper response, Instant now, long started,
            String error) {
        ExchangeLog entry = new ExchangeLog();
        entry.setCreatedAt(now);
        entry.setPartnerId((UUID) request.getAttribute(SnapAuthenticator.PARTNER_ID));
        entry.setDirection(Direction.INBOUND);
        entry.setMethod(request.getMethod());
        entry.setUrl(request.getRequestURI() + Optional.ofNullable(request.getQueryString()).map(q -> "?" + q).orElse(""));
        entry.setRequestHeaders(json.writeValueAsString(requestHeaders(request)));
        entry.setRequestBody(request.bodyText());
        entry.setStringToSign((String) request.getAttribute(SnapAuthenticator.STRING_TO_SIGN));
        entry.setResponseStatus(response.getStatus());
        Map<String, String> headers = new LinkedHashMap<>();
        response.getHeaderNames().forEach(name -> headers.put(name, response.getHeader(name)));
        entry.setResponseHeaders(json.writeValueAsString(headers));
        entry.setResponseBody(new String(response.getContentAsByteArray(), StandardCharsets.UTF_8));
        entry.setError(error);
        entry.setDurationMs((System.nanoTime() - started) / 1_000_000);
        exchangeLog.record(entry);
    }

    private static Map<String, String> requestHeaders(HttpServletRequest request) {
        Map<String, String> headers = new LinkedHashMap<>();
        for (String name : Collections.list(request.getHeaderNames())) {
            headers.put(name, request.getHeader(name));
        }
        return headers;
    }
}
