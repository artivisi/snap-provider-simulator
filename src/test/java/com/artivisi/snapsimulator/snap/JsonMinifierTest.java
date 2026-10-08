package com.artivisi.snapsimulator.snap;

import com.artivisi.snapsimulator.SpecRef;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpecRef("snap.sig.body-hash")
class JsonMinifierTest {

    @Test
    @DisplayName("Whitespace outside strings is removed, inside strings kept")
    void stripsOutsideStrings() {
        String pretty = "{\n  \"name\" : \"Budi  Santoso\",\r\n\t\"amount\": { \"value\": \"10000.00\" },\n  \"list\": [ 1, 2 ]\n}";
        assertThat(JsonMinifier.minify(pretty))
                .isEqualTo("{\"name\":\"Budi  Santoso\",\"amount\":{\"value\":\"10000.00\"},\"list\":[1,2]}");
    }

    @Test
    @DisplayName("Escaped quotes and backslashes do not end the string")
    void keepsEscapes() {
        String json = "{ \"a\" : \"x \\\" y \\\\\" , \"b\" : \"\\u0041 \" }";
        assertThat(JsonMinifier.minify(json)).isEqualTo("{\"a\":\"x \\\" y \\\\\",\"b\":\"\\u0041 \"}");
    }

    @Test
    @DisplayName("Numbers keep the sender's form")
    void numbersUntouched() {
        assertThat(JsonMinifier.minify("{ \"n\": 1.50, \"e\": 1E+2 }")).isEqualTo("{\"n\":1.50,\"e\":1E+2}");
    }

    @Test
    @DisplayName("Empty body stays empty")
    void emptyBody() {
        assertThat(JsonMinifier.minify("")).isEmpty();
    }

    @Test
    @DisplayName("Unterminated string or escape is rejected")
    void rejectsUnterminated() {
        assertThatThrownBy(() -> JsonMinifier.minify("{\"a\": \"b"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("inside a string");
        assertThatThrownBy(() -> JsonMinifier.minify("{\"a\": \"b\\"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("escape");
    }
}
