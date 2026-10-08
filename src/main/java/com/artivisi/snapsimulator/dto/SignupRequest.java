package com.artivisi.snapsimulator.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotNull @Min(SettingsRequest.MIN_TTL) @Max(SettingsRequest.MAX_TTL) Integer tokenTtlSeconds,
        @NotNull Boolean diagnosticMode) {
}
