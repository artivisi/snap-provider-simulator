package com.artivisi.snapsimulator.dto;

import tools.jackson.databind.JsonNode;

import java.util.UUID;

/** One bank-to-partner call as it happened; status is null when no HTTP response arrived. */
public record OutboundExchange(UUID logId, Integer status, String body, JsonNode json, String error) {

    public String responseCode() {
        return json == null || json.get("responseCode") == null ? null : json.get("responseCode").asString();
    }

    /** A text field under virtualAccountData, or null. */
    public String vaData(String field) {
        if (json == null) {
            return null;
        }
        JsonNode value = json.at("/virtualAccountData/" + field);
        return value.isMissingNode() || value.isNull() ? null : value.asString();
    }

    public String describe() {
        if (error != null) {
            return error;
        }
        return "HTTP " + status + (responseCode() == null ? "" : " responseCode " + responseCode());
    }
}
