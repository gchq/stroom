package stroom.pipeline.refdata;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

class TestEffectiveStream {

    private static final Instant TIME = LocalDateTime.of(2020, 6, 1, 11, 0)
            .toInstant(ZoneOffset.UTC);

    @Test
    void testEquals_true() {
        EffectiveStream effectiveStream1 = EffectiveStream.of(1, TIME);
        EffectiveStream effectiveStream2 = EffectiveStream.of(1, TIME);

        Assertions.assertThat(effectiveStream1)
                .isEqualTo(effectiveStream2);
        Assertions.assertThat(effectiveStream1.hashCode())
                .isEqualTo(effectiveStream2.hashCode());
    }

    @Test
    void testEquals_false1() {
        EffectiveStream effectiveStream1 = EffectiveStream.of(1, TIME);
        EffectiveStream effectiveStream2 = EffectiveStream.of(2, TIME);

        Assertions.assertThat(effectiveStream1)
                .isNotEqualTo(effectiveStream2);
    }

    @Test
    void testEquals_false2() {
        EffectiveStream effectiveStream1 = EffectiveStream.of(1, TIME);
        EffectiveStream effectiveStream2 = EffectiveStream.of(1, TIME.plus(1, ChronoUnit.HOURS));

        Assertions.assertThat(effectiveStream1)
                .isNotEqualTo(effectiveStream2);
    }

    @Test
    void testCompare_same() {
        EffectiveStream effectiveStream1 = EffectiveStream.of(1, TIME);
        EffectiveStream effectiveStream2 = EffectiveStream.of(2, TIME);

        // Compare only works on time, so are the same (compare wise)
        Assertions.assertThat(effectiveStream1.compareTo(effectiveStream2))
                .isEqualTo(0);
    }

    @Test
    void testCompare_greaterThan() {
        EffectiveStream effectiveStream1 = EffectiveStream.of(1, TIME);
        EffectiveStream effectiveStream2 = EffectiveStream.of(1, TIME.plus(1, ChronoUnit.HOURS));

        Assertions.assertThat(effectiveStream2)
                .isGreaterThan(effectiveStream1);
    }

    @Test
    void testCompare_lessThan() {
        EffectiveStream effectiveStream1 = EffectiveStream.of(1, TIME);
        EffectiveStream effectiveStream2 = EffectiveStream.of(1, TIME.plus(1, ChronoUnit.HOURS));

        Assertions.assertThat(effectiveStream1)
                .isLessThan(effectiveStream2);
    }
}
