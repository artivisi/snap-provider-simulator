package com.artivisi.snapsimulator.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record SettingsRequest(
        @NotNull @Min(MIN_TTL) @Max(MAX_TTL) Integer tokenTtlSeconds,
        @NotNull Boolean diagnosticMode) {

    public static final int MIN_TTL = 10;
    public static final int MAX_TTL = 86_400;
}
