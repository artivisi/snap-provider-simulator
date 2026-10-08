package com.artivisi.snapsimulator.dto;

import com.artivisi.snapsimulator.enums.Bank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** A new bank connection; every field is required. */
public record ConnectionRequest(
        @NotNull Bank bank,
        @NotNull @Min(SettingsRequest.MIN_TTL) @Max(SettingsRequest.MAX_TTL) Integer tokenTtlSeconds,
        @NotNull Boolean diagnosticMode) {
}
