package stroom.pathways.shared.otel.trace;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TestNanoTime {

    @Test
    void testToString() {
        assertThat(NanoTime.ZERO.toString()).isEqualTo("0ns");
        assertThat(NanoTime.ofNanos(102).toString()).isEqualTo("102ns");
        assertThat(NanoTime.ofMicros(2035).toString()).isEqualTo("2.04ms");
        assertThat(NanoTime.ofMillis(2035).toString()).isEqualTo("2.04s");
        assertThat(NanoTime.ofMillis(2300).toString()).isEqualTo("2.3s");
        assertThat(NanoTime.ofMillis(2000).toString()).isEqualTo("2s");
        assertThat(NanoTime.ofMillis(100).toString()).isEqualTo("100ms");
    }
}
