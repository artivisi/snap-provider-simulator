package com.artivisi.snapsimulator.snap;

import com.artivisi.snapsimulator.SpecRef;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpecRef("snap.sig.timestamp")
class SnapTimestampTest {

    @Test
    @DisplayName("Accepts offsets with and without milliseconds, and Z")
    void parsesWithOffset() {
        assertThat(SnapTimestamp.parse("2026-10-08T10:00:00.123+07:00").toInstant())
                .isEqualTo(Instant.parse("2026-10-08T03:00:00.123Z"));
        assertThat(SnapTimestamp.parse("2026-10-08T10:00:00+07:00").toInstant())
                .isEqualTo(Instant.parse("2026-10-08T03:00:00Z"));
        assertThat(SnapTimestamp.parse("2026-10-08T03:00:00Z").toInstant())
                .isEqualTo(Instant.parse("2026-10-08T03:00:00Z"));
    }

    @Test
    @DisplayName("Rejects a timestamp without offset")
    void rejectsNoOffset() {
        assertThatThrownBy(() -> SnapTimestamp.parse("2026-10-08T10:00:00.000"))
                .isInstanceOf(DateTimeParseException.class);
    }

    @Test
    @DisplayName("Generates Jakarta time with milliseconds and +07:00")
    void formatsJakarta() {
        assertThat(SnapTimestamp.format(Instant.parse("2026-10-08T03:00:00.005Z")))
                .isEqualTo("2026-10-08T10:00:00.005+07:00");
    }

    @Test
    @DisplayName("Skew check is inclusive in both directions")
    void skew() {
        Instant now = Instant.parse("2026-10-08T03:00:00Z");
        Duration skew = Duration.ofMinutes(5);
        assertThat(SnapTimestamp.withinSkew(SnapTimestamp.parse("2026-10-08T10:05:00+07:00"), now, skew)).isTrue();
        assertThat(SnapTimestamp.withinSkew(SnapTimestamp.parse("2026-10-08T09:55:00+07:00"), now, skew)).isTrue();
        assertThat(SnapTimestamp.withinSkew(SnapTimestamp.parse("2026-10-08T10:05:01+07:00"), now, skew)).isFalse();
    }
}
