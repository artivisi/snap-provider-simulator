package com.artivisi.snapsimulator.dto;

import com.artivisi.snapsimulator.enums.InjectionType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** delayMs and httpStatus are required only by the types that use them, and rejected otherwise. */
public record InjectionRuleRequest(
        @NotNull InjectionType type,
        @NotBlank String target,
        @Min(1) @Max(120_000) Long delayMs,
        Integer httpStatus,
        @NotNull @Min(1) @Max(100) Integer remaining) {
}
