package com.artivisi.snapsimulator.snap;

import com.artivisi.snapsimulator.SpecRef;

/** responseCode = HTTP status (3) + service code (2) + case code (2). */
@SpecRef("snap.response-code")
public record ResponseCode(int httpStatus, String serviceCode, String caseCode, String message) {

    public ResponseCode {
        if (httpStatus < 100 || httpStatus > 599) {
            throw new IllegalArgumentException("HTTP status out of range: " + httpStatus);
        }
        if (!serviceCode.matches("\\d{2}") || !caseCode.matches("\\d{2}")) {
            throw new IllegalArgumentException("service and case code must be 2 digits: " + serviceCode + "/" + caseCode);
        }
    }

    public String code() {
        return httpStatus + serviceCode + caseCode;
    }
}
