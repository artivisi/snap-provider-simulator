package com.artivisi.snapsimulator.dto;

import java.time.Instant;
import java.util.Map;

public record ErrorResponse(String error, String message, Map<String, String> fieldErrors, Instant timestamp) {

    public ErrorResponse {
        fieldErrors = Map.copyOf(fieldErrors);
    }
}
