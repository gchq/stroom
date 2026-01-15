package stroom.util.shared.time;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TestSimpleDuration {

    @Test
    void testParse() {
        for (final TimeUnit timeUnit : TimeUnit.values()) {
            final SimpleDuration in = new SimpleDuration(1000, timeUnit);
            final String str = in.toString();
            final SimpleDuration out = SimpleDuration.parse(str);
            assertThat(in).isEqualTo(out);
        }
    }
}
