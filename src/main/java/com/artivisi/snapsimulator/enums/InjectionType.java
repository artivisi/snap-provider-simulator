package com.artivisi.snapsimulator.enums;

import java.util.Set;

/** Failure the simulator injects into the next matching call(s). */
public enum InjectionType {
    /** Process and commit, then wait delayMs before answering: the client times out on a done transaction. */
    TIMEOUT_AFTER_PROCESSING(Kind.INBOUND, true, false),
    /** Wait delayMs, then process normally. */
    SLOW_RESPONSE(Kind.INBOUND, true, false),
    /** Answer httpStatus (500, 502 or 503) without processing. */
    HTTP_ERROR(Kind.INBOUND, false, true),
    /** Send a wrong X-SIGNATURE to the partner. */
    INVALID_SIGNATURE(Kind.OUTBOUND, false, false),
    /** Send the payment call delayMs late. */
    LATE_NOTIFICATION(Kind.PAYMENT, true, false),
    /** Do not send the payment call; the ledger still credits. */
    DROP_NOTIFICATION(Kind.PAYMENT, false, false);

    public static final Set<Integer> HTTP_ERROR_STATUSES = Set.of(500, 502, 503);

    public enum Kind {
        /** Targets an inbound SNAP service. */
        INBOUND,
        /** Targets an outbound call: INQUIRY or PAYMENT. */
        OUTBOUND,
        /** Targets the outbound PAYMENT call only. */
        PAYMENT
    }

    private final Kind kind;
    private final boolean needsDelay;
    private final boolean needsStatus;

    InjectionType(Kind kind, boolean needsDelay, boolean needsStatus) {
        this.kind = kind;
        this.needsDelay = needsDelay;
        this.needsStatus = needsStatus;
    }

    public Kind kind() {
        return kind;
    }

    public boolean needsDelay() {
        return needsDelay;
    }

    public boolean needsStatus() {
        return needsStatus;
    }
}
