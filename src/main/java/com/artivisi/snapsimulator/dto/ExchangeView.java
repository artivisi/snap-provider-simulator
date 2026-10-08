package com.artivisi.snapsimulator.dto;

import com.artivisi.snapsimulator.entity.ExchangeLog;
import com.artivisi.snapsimulator.enums.Direction;

import java.time.Instant;
import java.util.UUID;

public record ExchangeView(UUID id, Instant createdAt, Direction direction, String method, String url,
        String requestHeaders, String requestBody, String stringToSign, Integer responseStatus,
        String responseHeaders, String responseBody, String error, long durationMs) {

    public static ExchangeView of(ExchangeLog l) {
        return new ExchangeView(l.getId(), l.getCreatedAt(), l.getDirection(), l.getMethod(), l.getUrl(),
                l.getRequestHeaders(), l.getRequestBody(), l.getStringToSign(), l.getResponseStatus(),
                l.getResponseHeaders(), l.getResponseBody(), l.getError(), l.getDurationMs());
    }
}
