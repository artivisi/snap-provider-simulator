package com.artivisi.snapsimulator.exception;

import com.artivisi.snapsimulator.snap.ResponseCode;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Ends a SNAP call with the given response code; diagnostic is null unless diagnostic mode applies. */
public class SnapException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient ResponseCode responseCode;
    private final transient Map<String, String> diagnostic;

    public SnapException(ResponseCode responseCode) {
        this(responseCode, null);
    }

    public SnapException(ResponseCode responseCode, Map<String, String> diagnostic) {
        super(responseCode.code() + " " + responseCode.message());
        this.responseCode = responseCode;
        this.diagnostic = diagnostic == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(diagnostic));
    }

    public ResponseCode responseCode() {
        return responseCode;
    }

    public Map<String, String> diagnostic() {
        return diagnostic;
    }
}
