package com.artivisi.snapsimulator.snap;

import com.artivisi.snapsimulator.SpecRef;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpecRef("snap.response-code")
class ResponseCodeTest {

    @Test
    @DisplayName("Code is HTTP status + service code + case code")
    void composes() {
        assertThat(new ResponseCode(401, "27", "00", "Unauthorized").code()).isEqualTo("4012700");
    }

    @Test
    @DisplayName("Invalid parts are rejected")
    void rejectsInvalid() {
        assertThatThrownBy(() -> new ResponseCode(42, "27", "00", "x")).hasMessageContaining("HTTP status");
        assertThatThrownBy(() -> new ResponseCode(200, "7", "00", "x")).hasMessageContaining("2 digits");
        assertThatThrownBy(() -> new ResponseCode(200, "27", "0a", "x")).hasMessageContaining("2 digits");
    }
}
