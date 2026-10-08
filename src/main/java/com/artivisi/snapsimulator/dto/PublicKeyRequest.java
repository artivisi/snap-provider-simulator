package com.artivisi.snapsimulator.dto;

import jakarta.validation.constraints.NotBlank;

public record PublicKeyRequest(@NotBlank String publicKeyPem) {
}
