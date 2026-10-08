package com.artivisi.snapsimulator.snap;

import com.artivisi.snapsimulator.SpecRef;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/** X-TIMESTAMP parsing (offset required, A5) and generation in Asia/Jakarta (A7). */
@SpecRef("snap.sig.timestamp")
public final class SnapTimestamp {

    public static final ZoneId JAKARTA = ZoneId.of("Asia/Jakarta");

    private static final DateTimeFormatter OUTPUT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
    private static final DateTimeFormatter OUTPUT_SECONDS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    private SnapTimestamp() {
    }

    /** @throws DateTimeParseException when the value is not ISO-8601 with an explicit offset */
    public static OffsetDateTime parse(String value) {
        return OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    public static String format(Instant instant) {
        return OUTPUT.format(instant.atZone(JAKARTA));
    }

    /** Date-time body fields of length 25 (expiredDate, lastUpdateDate, paymentDate, trxDateTime). */
    public static String formatSeconds(Instant instant) {
        return OUTPUT_SECONDS.format(instant.atZone(JAKARTA));
    }

    public static boolean withinSkew(OffsetDateTime timestamp, Instant now, Duration skew) {
        return Duration.between(timestamp.toInstant(), now).abs().compareTo(skew) <= 0;
    }
}
