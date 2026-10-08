package com.artivisi.snapsimulator.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** The partner app's base URL and the credentials it issued to the bank. */
public record EndpointRequest(
        @NotBlank @Size(max = 500) @Pattern(regexp = "https?://\\S+", message = "must be an http or https URL") String baseUrl,
        @NotBlank @Size(max = 128) String clientId,
        @NotBlank @Size(max = 256) String clientSecret) {
}
