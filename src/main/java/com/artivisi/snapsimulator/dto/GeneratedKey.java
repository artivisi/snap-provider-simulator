package com.artivisi.snapsimulator.dto;

/** The private key is returned once and not stored. */
public record GeneratedKey(String publicKeyPem, String privateKeyPem) {
}
