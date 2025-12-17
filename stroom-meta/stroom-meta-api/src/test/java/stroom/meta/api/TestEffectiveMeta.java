/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.meta.api;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

class TestEffectiveMeta {

    private static final Instant TIME = LocalDateTime.of(2020, 6, 1, 11, 0)
            .toInstant(ZoneOffset.UTC);

    @Test
    void testEquals_true() {
        final EffectiveMeta effectiveStream1 = buildEffectiveMeta(1, TIME);
        final EffectiveMeta effectiveStream2 = buildEffectiveMeta(1, TIME);

        Assertions.assertThat(effectiveStream1)
                .isEqualTo(effectiveStream2);
        Assertions.assertThat(effectiveStream1.hashCode())
                .isEqualTo(effectiveStream2.hashCode());
    }

    @Test
    void testEquals_true_diffFeed() {
        // Feed not part of equals
        final EffectiveMeta effectiveStream1 = EffectiveMeta.of(1, "FEED1", "MyType", TIME);
        final EffectiveMeta effectiveStream2 = EffectiveMeta.of(1, "FEED2", "MyType", TIME);

        Assertions.assertThat(effectiveStream1)
                .isEqualTo(effectiveStream2);
        Assertions.assertThat(effectiveStream1.hashCode())
                .isEqualTo(effectiveStream2.hashCode());
    }

    @Test
    void testEquals_true_diffType() {
        // Type not part of equals
        final EffectiveMeta effectiveStream1 = EffectiveMeta.of(1, "FEED", "MyType1", TIME);
        final EffectiveMeta effectiveStream2 = EffectiveMeta.of(1, "FEED", "MyType2", TIME);

        Assertions.assertThat(effectiveStream1)
                .isEqualTo(effectiveStream2);
        Assertions.assertThat(effectiveStream1.hashCode())
                .isEqualTo(effectiveStream2.hashCode());
    }

    @Test
    void testEquals_false1() {
        final EffectiveMeta effectiveStream1 = buildEffectiveMeta(1, TIME);
        final EffectiveMeta effectiveStream2 = buildEffectiveMeta(2, TIME);

        Assertions.assertThat(effectiveStream1)
                .isNotEqualTo(effectiveStream2);
    }

    @Test
    void testEquals_false2() {
        final EffectiveMeta effectiveStream1 = buildEffectiveMeta(1, TIME);
        final EffectiveMeta effectiveStream2 = buildEffectiveMeta(1, TIME.plus(1, ChronoUnit.HOURS));

        Assertions.assertThat(effectiveStream1)
                .isNotEqualTo(effectiveStream2);
    }

//    @Test
//    void testCompare_same() {
//        EffectiveMeta effectiveStream1 = buildEffectiveMeta(1, TIME);
//        EffectiveMeta effectiveStream2 = buildEffectiveMeta(2, TIME);
//
//        // Compare only works on time, so are the same (compare wise)
//        Assertions.assertThat(effectiveStream1.compareTo(effectiveStream2))
//                .isEqualTo(0);
//    }
//
//    @Test
//    void testCompare_greaterThan() {
//        EffectiveMeta effectiveStream1 = buildEffectiveMeta(1, TIME);
//        EffectiveMeta effectiveStream2 = buildEffectiveMeta(1, TIME.plus(1, ChronoUnit.HOURS));
//
//        Assertions.assertThat(effectiveStream2)
//                .isGreaterThan(effectiveStream1);
//    }
//
//    @Test
//    void testCompare_lessThan() {
//        EffectiveMeta effectiveStream1 = buildEffectiveMeta(1, TIME);
//        EffectiveMeta effectiveStream2 = buildEffectiveMeta(1, TIME.plus(1, ChronoUnit.HOURS));
//
//        Assertions.assertThat(effectiveStream1)
//                .isLessThan(effectiveStream2);
//    }

    private static EffectiveMeta buildEffectiveMeta(final long id, final Instant effectiveTime) {
        return new EffectiveMeta(id, "DUMMY_FEED", "DummyType", effectiveTime);
    }
}
