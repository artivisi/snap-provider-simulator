package com.artivisi.snapsimulator.controller.snap;

import com.artivisi.snapsimulator.exception.SnapException;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/** SNAP error body; additionalInfo only carries the diagnostic, when there is one. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SnapErrorBody(String responseCode, String responseMessage, Map<String, Object> additionalInfo) {

    public SnapErrorBody {
        additionalInfo = additionalInfo == null ? null : Map.copyOf(additionalInfo);
    }

    public static SnapErrorBody of(SnapException e) {
        return new SnapErrorBody(e.responseCode().code(), e.responseCode().message(),
                e.diagnostic() == null ? null : Map.of("diagnostic", e.diagnostic()));
    }
}
