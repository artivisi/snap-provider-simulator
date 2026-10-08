package com.artivisi.snapsimulator.snap;

import java.util.Arrays;
import java.util.Optional;

/** Inbound SNAP services: method, path and the service code used in responseCode. */
public enum SnapService {
    ACCESS_TOKEN_B2B("POST", "/snap/v1.0/access-token/b2b", "73"),
    CREATE_VA("POST", "/snap/v1.0/transfer-va/create-va", "27"),
    UPDATE_VA("PUT", "/snap/v1.0/transfer-va/update-va", "28"),
    INQUIRY_VA("POST", "/snap/v1.0/transfer-va/inquiry-va", "30"),
    DELETE_VA("DELETE", "/snap/v1.0/transfer-va/delete-va", "31"),
    INQUIRY_STATUS("POST", "/snap/v1.0/transfer-va/status", "26");

    private final String method;
    private final String path;
    private final String code;

    SnapService(String method, String path, String code) {
        this.method = method;
        this.path = path;
        this.code = code;
    }

    public String method() {
        return method;
    }

    public String path() {
        return path;
    }

    public String code() {
        return code;
    }

    public static Optional<SnapService> match(String method, String path) {
        return Arrays.stream(values()).filter(s -> s.method.equals(method) && s.path.equals(path)).findFirst();
    }

    public ResponseCode success() {
        return new ResponseCode(200, code, "00", "Successful");
    }

    public ResponseCode badRequest() {
        return new ResponseCode(400, code, "00", "Bad Request");
    }

    public ResponseCode invalidFieldFormat(String field) {
        return new ResponseCode(400, code, "01", "Invalid Field Format " + field);
    }

    public ResponseCode invalidMandatoryField(String field) {
        return new ResponseCode(400, code, "02", "Invalid Mandatory Field " + field);
    }

    public ResponseCode unauthorized(String reason) {
        return new ResponseCode(401, code, "00", "Unauthorized. " + reason);
    }

    public ResponseCode invalidToken() {
        return new ResponseCode(401, code, "01", "Invalid Token (B2B)");
    }

    public ResponseCode conflict() {
        return new ResponseCode(409, code, "00", "Conflict");
    }

    /** 404 with a service-specific case code, e.g. 12 invalid bill, 14 paid bill, 16 partner not found. */
    public ResponseCode notFound(String caseCode, String message) {
        return new ResponseCode(404, code, caseCode, message);
    }

    public ResponseCode conflict(String caseCode, String message) {
        return new ResponseCode(409, code, caseCode, message);
    }

    public ResponseCode generalError() {
        return new ResponseCode(500, code, "00", "General Error");
    }
}
