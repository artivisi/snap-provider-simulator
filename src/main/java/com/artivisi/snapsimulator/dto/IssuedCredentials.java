package com.artivisi.snapsimulator.dto;

/** Returned once, at sign-up and on regeneration; the secret is not shown again. */
public record IssuedCredentials(String partnerServiceId, String clientId, String clientSecret) {
}
