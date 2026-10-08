package com.artivisi.snapsimulator.snap;

import com.artivisi.snapsimulator.exception.SnapException;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

/**
 * Reads fields of a SNAP request body, answering 400xx02 for a missing mandatory
 * field and 400xx01 for a wrong type, length or format, naming the field path.
 */
public final class SnapRequest {

    private static final Pattern AMOUNT = Pattern.compile("\\d{1,16}\\.\\d{2}");
    private static final Pattern CURRENCY = Pattern.compile("[A-Z]{3}");

    private final JsonNode body;
    private final SnapService svc;

    private SnapRequest(JsonNode body, SnapService svc) {
        this.body = body;
        this.svc = svc;
    }

    public static SnapRequest of(JsonNode body, SnapService svc) {
        if (body == null || !body.isObject()) {
            throw new SnapException(svc.badRequest());
        }
        return new SnapRequest(body, svc);
    }

    public JsonNode node() {
        return body;
    }

    public SnapService service() {
        return svc;
    }

    public String text(String name, boolean mandatory, int maxLength) {
        JsonNode value = body.get(name);
        if (value == null || value.isNull() || (value.isString() && value.asString().isEmpty())) {
            if (mandatory) {
                throw new SnapException(svc.invalidMandatoryField(name));
            }
            return null;
        }
        if (!value.isString() || value.asString().length() > maxLength) {
            throw new SnapException(svc.invalidFieldFormat(name));
        }
        return value.asString();
    }

    public String text(String name, boolean mandatory, Pattern format) {
        String value = text(name, mandatory, Integer.MAX_VALUE);
        if (value != null && !format.matcher(value).matches()) {
            throw new SnapException(svc.invalidFieldFormat(name));
        }
        return value;
    }

    /** An {value, currency} object; null when optional and absent. */
    public Amount amount(String name, boolean mandatory) {
        JsonNode value = body.get(name);
        if (value == null || value.isNull()) {
            if (mandatory) {
                throw new SnapException(svc.invalidMandatoryField(name));
            }
            return null;
        }
        if (!value.isObject()) {
            throw new SnapException(svc.invalidFieldFormat(name));
        }
        return new Amount(amountPart(value, name, "value", AMOUNT), amountPart(value, name, "currency", CURRENCY));
    }

    private String amountPart(JsonNode amount, String name, String part, Pattern format) {
        JsonNode value = amount.get(part);
        if (value == null || value.isNull() || (value.isString() && value.asString().isEmpty())) {
            throw new SnapException(svc.invalidMandatoryField(name + "." + part));
        }
        if (!value.isString() || !format.matcher(value.asString()).matches()) {
            throw new SnapException(svc.invalidFieldFormat(name + "." + part));
        }
        return value.asString();
    }

    /** ISO-8601 with offset (A21); null when absent. */
    public Instant dateTime(String name) {
        String value = text(name, false, 25);
        if (value == null) {
            return null;
        }
        try {
            return SnapTimestamp.parse(value).toInstant();
        } catch (DateTimeParseException e) {
            throw new SnapException(svc.invalidFieldFormat(name));
        }
    }

    public record Amount(String value, String currency) {

        public BigDecimal decimal() {
            return new BigDecimal(value);
        }
    }
}
