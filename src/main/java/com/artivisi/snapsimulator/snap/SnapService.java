package com.artivisi.snapsimulator.snap;

import com.artivisi.snapsimulator.enums.Bank;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** Inbound SNAP services per bank: method, path and the service code used in responseCode. */
public enum SnapService {
    BRI_ACCESS_TOKEN(Bank.BRI, "POST", "/snap/v1.0/access-token/b2b", "73"),
    BRI_CREATE_VA(Bank.BRI, "POST", "/snap/v1.0/transfer-va/create-va", "27"),
    BRI_UPDATE_VA(Bank.BRI, "PUT", "/snap/v1.0/transfer-va/update-va", "28"),
    BRI_INQUIRY_VA(Bank.BRI, "POST", "/snap/v1.0/transfer-va/inquiry-va", "30"),
    BRI_DELETE_VA(Bank.BRI, "DELETE", "/snap/v1.0/transfer-va/delete-va", "31"),
    BRI_INQUIRY_STATUS(Bank.BRI, "POST", "/snap/v1.0/transfer-va/status", "26"),
    BCA_ACCESS_TOKEN(Bank.BCA, "POST", "/openapi/v1.0/access-token/b2b", "73"),
    BCA_INQUIRY_STATUS(Bank.BCA, "POST", "/openapi/v2.0/transfer-va/status", "26");

    private final Bank bank;
    private final String method;
    private final String path;
    private final String code;

    SnapService(Bank bank, String method, String path, String code) {
        this.bank = bank;
        this.method = method;
        this.path = path;
        this.code = code;
    }

    public Bank bank() {
        return bank;
    }

    public boolean isToken() {
        return this == BRI_ACCESS_TOKEN || this == BCA_ACCESS_TOKEN;
    }

    public static List<SnapService> of(Bank bank) {
        return Arrays.stream(values()).filter(s -> s.bank == bank).toList();
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
