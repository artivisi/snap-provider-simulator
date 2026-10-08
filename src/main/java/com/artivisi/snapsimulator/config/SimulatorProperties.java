package com.artivisi.snapsimulator.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Deployment-wide settings. Every value is required and comes from the
 * environment (see application.yml); there are no defaults.
 */
@Validated
@ConfigurationProperties("simulator")
public record SimulatorProperties(
        @NotNull Duration timestampSkew,
        @NotNull @Valid Bank bank,
        @NotNull @Valid Operator operator,
        @NotNull @Valid Outbound outbound) {

    /** An unset env var reaches the binder as the literal placeholder. */
    static final String NOT_PLACEHOLDER = "^(?!\\$\\{).*";

    /** keyDir holds one PKCS#8 key per bank: bri-private.pem, bca-private.pem. */
    public record Bank(@NotBlank @Pattern(regexp = NOT_PLACEHOLDER, message = "env var not set") String keyDir) {
    }

    public record Operator(
            @NotBlank @Pattern(regexp = NOT_PLACEHOLDER, message = "env var not set") String username,
            @NotBlank @Pattern(regexp = NOT_PLACEHOLDER, message = "env var not set") String password) {
    }

    public record Outbound(@NotNull Duration connectTimeout, @NotNull Duration readTimeout) {
    }
}
